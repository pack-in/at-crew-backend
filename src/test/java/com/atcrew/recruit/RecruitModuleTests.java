package com.atcrew.recruit;

import com.atcrew.TestMongoConfig;
import com.atcrew.common.exception.DomainException;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// recruit은 member 공개 API에 의존하므로 추이적 의존성까지 부트스트랩한다(CommunityModuleTests와 동일 이유).
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
@Import(TestMongoConfig.class)
class RecruitModuleTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    RecruitService recruitService;

    @Autowired
    MemberService memberService;

    @Test
    void 끌어올린_구인글이_공개_목록_상단에_노출된다() {
        String authorId = registerMember("boost-author");
        String olderId = publishedJobPosting(authorId, "오래된 공고");
        String newerId = publishedJobPosting(authorId, "최신 공고");

        // 끌어올리기 전에는 최신 글이 앞
        assertThat(publishedIds()).containsSubsequence(newerId, olderId);

        recruitService.boostJobPosting(authorId, olderId);

        assertThat(publishedIds()).containsSubsequence(olderId, newerId);
    }

    @Test
    void 끌어올리기_적용_중_재적용하면_쿨다운으로_거부된다() {
        String authorId = registerMember("boost-cooldown");
        String jobPostingId = publishedJobPosting(authorId, "쿨다운 공고");

        JobPostingInfo boosted = recruitService.boostJobPosting(authorId, jobPostingId);
        assertThat(boosted.boostedUntil()).isNotNull();

        assertThatThrownBy(() -> recruitService.boostJobPosting(authorId, jobPostingId))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("BOOST_COOLDOWN");
    }

    @Test
    void 작성자가_아니면_끌어올릴_수_없다() {
        String authorId = registerMember("boost-owner");
        String strangerId = registerMember("boost-stranger");
        String jobPostingId = publishedJobPosting(authorId, "남의 공고");

        assertThatThrownBy(() -> recruitService.boostJobPosting(strangerId, jobPostingId))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("FORBIDDEN_NOT_AUTHOR");
    }

    @Test
    void 같은_구인글에_두_번_지원하면_중복으로_거부된다() {
        String authorId = registerMember("apply-author");
        String applicantId = registerMember("apply-applicant");
        String jobPostingId = publishedJobPosting(authorId, "지원 공고");
        CreateApplicationCommand command =
                new CreateApplicationCommand(SerialExperience.NEWCOMER, false, null);

        recruitService.applyToJobPosting(applicantId, jobPostingId, command);

        assertThatThrownBy(() -> recruitService.applyToJobPosting(applicantId, jobPostingId, command))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("DUPLICATE_APPLICATION");
    }

    @Test
    void 팀원모집글_지원자_목록은_작성자만_조회할_수_있다() {
        String authorId = registerMember("team-author");
        String applicantId = registerMember("team-applicant");
        String teamPostingId = recruitService.createTeamPosting(authorId, teamPostingCommand()).id();
        recruitService.applyToTeamPosting(applicantId, teamPostingId,
                new CreateApplicationCommand(SerialExperience.ONE_TO_TWO, true, null));

        assertThat(recruitService.getTeamPostingApplications(authorId, teamPostingId, null, 20).items())
                .extracting(ApplicationInfo::applicantMemberId)
                .containsExactly(applicantId);

        // laiteu에서 검증이 빠져 있던 지점 — 작성자가 아니면 조회 불가여야 한다(§2.6)
        assertThatThrownBy(() -> recruitService.getTeamPostingApplications(applicantId, teamPostingId, null, 20))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("FORBIDDEN_NOT_AUTHOR");
    }

    @Test
    void 지원자_채용_단계를_합격으로_변경한다() {
        String authorId = registerMember("review-author");
        String applicantId = registerMember("review-applicant");
        String jobPostingId = publishedJobPosting(authorId, "채용 단계 공고");
        ApplicationInfo application = recruitService.applyToJobPosting(applicantId, jobPostingId,
                new CreateApplicationCommand(SerialExperience.FIVE_PLUS, false, null));

        ApplicationInfo accepted = recruitService.updateJobApplicationReviewStatus(
                authorId, jobPostingId, application.id(), ApplicationReviewStatus.ACCEPTED);

        assertThat(accepted.reviewStatus()).isEqualTo(ApplicationReviewStatus.ACCEPTED);
    }

    @Test
    void 구직글은_게시해야_공개_목록에_노출된다() {
        String authorId = registerMember("seeking-author");
        JobSeekingPostInfo draft = recruitService.createJobSeekingPost(authorId, new CreateJobSeekingPostCommand(
                "구직글 제목", List.of("작화"), List.of("판타지"), "선화 위주",
                FeedbackStyle.PERIODIC, WorkStyle.COLLABORATIVE, "협의", "포트폴리오 소개",
                List.of("https://img.example/ref.png"), false));

        assertThat(draft.status()).isEqualTo(JobSeekingPostStatus.DRAFT);
        assertThat(jobSeekingPostIds()).doesNotContain(draft.id());

        recruitService.publishJobSeekingPost(authorId, draft.id());

        assertThat(jobSeekingPostIds()).contains(draft.id());
    }

    @Test
    void 관심_작가_저장과_해제가_목록에_반영된다() {
        String companyId = registerMember("liked-company");
        String artistId = registerMember("liked-artist");

        recruitService.likeArtist(companyId, artistId);
        recruitService.likeArtist(companyId, artistId); // 재저장은 무시(멱등)

        assertThat(recruitService.getLikedArtists(companyId, null, null, 20).items())
                .extracting(LikedArtistInfo::artistMemberId)
                .containsExactly(artistId);

        recruitService.unlikeArtist(companyId, artistId);

        assertThat(recruitService.getLikedArtists(companyId, null, null, 20).items()).isEmpty();
    }

    @Test
    void 관심_작가_검색어로_작가_이름을_거를_수_있다() {
        String companyId = registerMember("liked-search-company");
        String matchedArtistId = registerMemberWithName("liked-search-hit", "김앳크루");
        String otherArtistId = registerMemberWithName("liked-search-miss", "박라이트");

        recruitService.likeArtist(companyId, matchedArtistId);
        recruitService.likeArtist(companyId, otherArtistId);

        assertThat(recruitService.getLikedArtists(companyId, "앳크루", null, 20).items())
                .extracting(LikedArtistInfo::artistMemberId)
                .containsExactly(matchedArtistId);
        assertThat(recruitService.getLikedArtists(companyId, "없는이름", null, 20).items()).isEmpty();
        // 검색어가 없으면 저장한 작가 전체가 조회된다
        assertThat(recruitService.getLikedArtists(companyId, null, null, 20).items())
                .extracting(LikedArtistInfo::artistMemberId)
                .containsExactlyInAnyOrder(matchedArtistId, otherArtistId);
    }

    @Test
    void 공개_구인글이_있어야_구인글_보유로_판정된다() {
        String authorId = registerMember("open-posting-author");

        assertThat(recruitService.hasOpenJobPosting(authorId)).isFalse();

        String jobPostingId = publishedJobPosting(authorId, "채용 중 공고");
        assertThat(recruitService.hasOpenJobPosting(authorId)).isTrue();

        recruitService.closeJobPosting(authorId, jobPostingId);
        assertThat(recruitService.hasOpenJobPosting(authorId)).isFalse();
    }

    @Test
    void 검색은_공개된_3종을_제목과_태그로_조회한다() {
        // 같은 DB를 공유하는 다른 테스트의 글과 섞이지 않도록 이 테스트 전용 검색 토큰을 제목에 넣는다.
        String token = uniqueToken();
        String authorId = registerMember("search-author");
        String jobPostingId = publishedJobPosting(authorId, token + " 구인 공고");
        String teamPostingId = recruitService.createTeamPosting(authorId, teamPostingCommand()).id();
        JobSeekingPostInfo seekingPost = recruitService.createJobSeekingPost(
                authorId, jobSeekingPostCommand(token + " 구직글"));
        recruitService.publishJobSeekingPost(authorId, seekingPost.id());

        // 제목 검색 — 토큰이 들어간 구인글·구직글만 일치(팀원모집글 제목에는 토큰이 없다)
        RecruitSearchPage byTitle = recruitService.searchPosts(new RecruitSearchQuery(
                token, null, null, null, null, null, 20));
        assertThat(byTitle.items()).extracting(RecruitSearchResultInfo::id)
                .containsExactlyInAnyOrder(jobPostingId, seekingPost.id());
        assertThat(byTitle.totalCount()).isEqualTo(2);

        // 유형 필터 — 구직글만 조회
        RecruitSearchPage seekingOnly = recruitService.searchPosts(new RecruitSearchQuery(
                token, List.of(RecruitPostType.JOB_SEEKING), null, null, null, null, 20));
        assertThat(seekingOnly.items()).extracting(RecruitSearchResultInfo::id)
                .containsExactly(seekingPost.id());
        assertThat(seekingOnly.items()).extracting(RecruitSearchResultInfo::postType)
                .containsOnly(RecruitPostType.JOB_SEEKING);

        // 태그 필터 — 팀원모집글 장르(액션)에는 걸리고, 구인글 장르(로맨스)에는 걸리지 않는다
        RecruitSearchPage byGenre = recruitService.searchPosts(new RecruitSearchQuery(
                null, null, null, List.of("액션"), null, null, 20));
        assertThat(byGenre.items()).extracting(RecruitSearchResultInfo::id).contains(teamPostingId);
        assertThat(byGenre.items()).extracting(RecruitSearchResultInfo::id).doesNotContain(jobPostingId);
    }

    @Test
    void 검색은_공개되지_않은_글을_제외한다() {
        String token = uniqueToken();
        String authorId = registerMember("search-draft-author");
        // 커맨드의 submit=true라 승인 전 PENDING 상태이며, 공개 검색 대상이 아니다
        JobPostingInfo pending = recruitService.createJobPosting(authorId, jobPostingCommand(token + " 미공개 공고"));

        assertThat(searchIds(token)).isEmpty();

        String published = recruitService.approveJobPosting(pending.id()).id();

        assertThat(searchIds(token)).containsExactly(published);
    }

    @Test
    void 검색_결과는_커서로_이어서_조회된다() {
        String token = uniqueToken();
        String authorId = registerMember("search-cursor-author");
        String firstId = publishedJobPosting(authorId, token + " 공고 1");
        String secondId = publishedJobPosting(authorId, token + " 공고 2");

        RecruitSearchPage firstPage = recruitService.searchPosts(new RecruitSearchQuery(
                token, null, null, null, null, null, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.totalCount()).isEqualTo(2);

        RecruitSearchResultInfo last = firstPage.items().get(0);
        RecruitSearchPage secondPage = recruitService.searchPosts(new RecruitSearchQuery(
                token, null, null, null, last.createdAt(), last.id(), 1));

        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(List.of(last.id(), secondPage.items().get(0).id()))
                .containsExactlyInAnyOrder(firstId, secondId);
    }

    @Test
    void 최근_본_작가는_재조회하면_조회_시각만_갱신된다() {
        String companyId = registerMember("viewed-company");
        String artistId = registerMember("viewed-artist");

        recruitService.recordArtistView(companyId, artistId);
        Instant firstViewedAt = recruitService.getRecentlyViewedArtists(companyId, null, 20).items()
                .get(0).viewedAt();

        recruitService.recordArtistView(companyId, artistId);

        List<RecentlyViewedArtistInfo> viewed = recruitService.getRecentlyViewedArtists(companyId, null, 20).items();
        assertThat(viewed).extracting(RecentlyViewedArtistInfo::artistMemberId).containsExactly(artistId);
        assertThat(viewed.get(0).viewedAt()).isAfterOrEqualTo(firstViewedAt);
    }

    private List<String> publishedIds() {
        return recruitService.getJobPostings(null, 50).items().stream().map(JobPostingInfo::id).toList();
    }

    private List<String> jobSeekingPostIds() {
        return recruitService.getJobSeekingPosts(null, 50).items().stream().map(JobSeekingPostInfo::id).toList();
    }

    // 다른 테스트의 글과 겹치지 않는 검색 토큰
    private String uniqueToken() {
        return "TOKEN" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private List<String> searchIds(String q) {
        return recruitService.searchPosts(new RecruitSearchQuery(q, null, null, null, null, null, 20))
                .items().stream().map(RecruitSearchResultInfo::id).toList();
    }

    private String registerMember(String handlePrefix) {
        return registerMemberWithName(handlePrefix, "테스터");
    }

    private String registerMemberWithName(String handlePrefix, String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberService.register(
                handlePrefix + "-" + suffix + "@atcrew.com", handlePrefix + suffix, name, CreatorRole.WEBTOON).id();
    }

    // 작성 → 제출 → 관리자 승인까지 마친 PUBLISHED 구인글 ID를 반환한다.
    private String publishedJobPosting(String authorMemberId, String title) {
        JobPostingInfo created = recruitService.createJobPosting(authorMemberId, jobPostingCommand(title));
        return recruitService.approveJobPosting(created.id()).id();
    }

    private CreateJobPostingCommand jobPostingCommand(String title) {
        return new CreateJobPostingCommand(
                title, "앳크루", "대표", "웹툰", "서울", "02-000-0000", "https://example.com",
                "회사 소개", true, true, false,
                List.of("작화"), List.of("로맨스"), "작업 범위", null, 2, "서류 → 면접",
                "무관", "신입", "무관", "무관",
                JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE, JobWorkScheduleType.FIXED,
                null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 3000L, 4000L, true,
                null, null, false, "복지 설명", List.of("식대"),
                "https://img.example/thumb.png", List.of("https://img.example/ref.png"), true);
    }

    private CreateJobSeekingPostCommand jobSeekingPostCommand(String title) {
        return new CreateJobSeekingPostCommand(
                title, List.of("작화"), List.of("판타지"), "선화 위주",
                FeedbackStyle.PERIODIC, WorkStyle.COLLABORATIVE, "협의", "포트폴리오 소개",
                List.of("https://img.example/ref.png"), false);
    }

    private CreateTeamPostingCommand teamPostingCommand() {
        return new CreateTeamPostingCommand(
                "팀원 모집", false, false, false, "팀장", "010-0000-0000", "팀 소개",
                List.of("공모전"), TeamWorkLocationType.ONLINE, null,
                List.of("배경"), List.of("액션"), false, true, null, null, 3, "포트폴리오 심사",
                TeamActivityDuration.THREE_MONTHS, TeamWeeklyActivityTime.TWO_TO_THREE_TIMES,
                "프로젝트 소개", "https://img.example/team.png", List.of());
    }
}
