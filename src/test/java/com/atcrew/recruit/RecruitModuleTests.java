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

        assertThat(recruitService.getLikedArtists(companyId, null, 20).items())
                .extracting(LikedArtistInfo::artistMemberId)
                .containsExactly(artistId);

        recruitService.unlikeArtist(companyId, artistId);

        assertThat(recruitService.getLikedArtists(companyId, null, 20).items()).isEmpty();
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

    private String registerMember(String handlePrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberService.register(
                handlePrefix + "-" + suffix + "@atcrew.com", handlePrefix + suffix, "테스터", CreatorRole.WEBTOON).id();
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

    private CreateTeamPostingCommand teamPostingCommand() {
        return new CreateTeamPostingCommand(
                "팀원 모집", false, false, false, "팀장", "010-0000-0000", "팀 소개",
                List.of("공모전"), TeamWorkLocationType.ONLINE, null,
                List.of("배경"), List.of("액션"), false, true, null, null, 3, "포트폴리오 심사",
                TeamActivityDuration.THREE_MONTHS, TeamWeeklyActivityTime.TWO_TO_THREE_TIMES,
                "프로젝트 소개", "https://img.example/team.png", List.of());
    }
}
