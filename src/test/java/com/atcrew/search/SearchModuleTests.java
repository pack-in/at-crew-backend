package com.atcrew.search;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.TeamActivityDuration;
import com.atcrew.recruit.TeamWeeklyActivityTime;
import com.atcrew.recruit.TeamWorkLocationType;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.search.internal.application.ArtworkReindexService;
import com.atcrew.search.internal.application.RecruitReindexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * search 모듈 통합 테스트 — artwork 업로드 → 색인 반영(비동기) → 필터 조합 검색까지 전체 파이프라인을 검증한다.
 *
 * <p>search는 artwork·recruit에 의존(SearchServiceImpl, ArtworkSearchIndexer)하므로 DIRECT_DEPENDENCIES로는
 * 그 모듈들의 추이적 의존성(memberService 등)까지 부트스트랩되지 않는다 — community 모듈 테스트와 동일한 이유로
 * ALL_DEPENDENCIES를 사용한다.
 *
 * <p>MariaDB 전환(docs/design/mariadb-migration-design.md) 이후 spring-boot-starter-data-jpa가
 * 무조건 오토컨피규레이션되어 이 모듈이 MariaDB를 쓰지 않아도 DataSource 빈 생성에 컨테이너가 필요하다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class SearchModuleTests {

    @Autowired
    EntitlementBalanceRepository balanceRepository;

    @Container
    @ServiceConnection
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:9.2.8")
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    SearchService searchService;

    @Autowired
    RecruitService recruitService;

    @Autowired
    ArtworkReindexService reindexService;

    @Autowired
    RecruitReindexService recruitReindexService;

    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 전체_재색인_후에도_기존_작품이_검색된다() {
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.ANIMATION, CreativeType.COMMISSION,
                List.of(ArtworkRole.BACKGROUND), List.of(Genre.SF), AgeRating.ALL);
        awaitSearchResult(() -> searchService.search(queryWithArtworkField(ArtworkField.ANIMATION)));

        // alias(artworks)를 새 물리 인덱스로 원자적으로 전환 — docs/design/search-module-design.md §5.3
        reindexService.reindexAll();

        List<SearchResultItem> found = awaitSearchResult(
                () -> searchService.search(queryWithArtworkField(ArtworkField.ANIMATION)));
        assertThat(found).extracting(SearchResultItem::id).contains(artwork.id());
    }

    @Test
    void 업로드된_작품이_비동기로_색인되어_검색에_노출된다() {
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of(Genre.BL), AgeRating.ALL);

        List<SearchResultItem> found = awaitSearchResult(
                () -> searchService.search(queryWithArtworkField(ArtworkField.ILLUSTRATION)));

        assertThat(found).extracting(SearchResultItem::id).contains(artwork.id());
    }

    @Test
    void 필터가_일치하지_않으면_결과에서_제외된다() {
        uploadReadyArtwork(ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of(Genre.FANTASY), AgeRating.ALL);

        // 색인 반영을 기다린 뒤(존재 확인) 다른 필터로는 조회되지 않는지 검증
        awaitSearchResult(() -> searchService.search(queryWithArtworkField(ArtworkField.WEBTOON)));

        SearchPage<SearchResultItem> mismatched = searchService.search(new SearchQuery(
                null, null, List.of(ArtworkField.ANIMATION), null, null, null, null, null,
                null, null, 20));

        assertThat(mismatched.items()).isEmpty();
    }

    @Test
    void 구인글_유형만_요청하면_recruit_결과를_반환한다() {
        String token = uniqueToken();
        String authorId = registerMember();
        String jobPostingId = publishedJobPosting(authorId, token + " 구인 공고");

        // RecruitSearchIndexer도 ArtworkSearchIndexer와 동일하게 @ApplicationModuleListener(비동기)라 폴링한다.
        List<SearchResultItem> found = awaitSearchResult(() -> searchService.search(new SearchQuery(
                token, List.of(PostType.JOB_POSTING), null, null, null, null, null, null, null, null, 20)));

        assertThat(found).extracting(SearchResultItem::id).containsExactly(jobPostingId);
        assertThat(found).extracting(SearchResultItem::postType).containsOnly(PostType.JOB_POSTING);
        SearchPage<SearchResultItem> page = searchService.search(new SearchQuery(
                token, List.of(PostType.JOB_POSTING), null, null, null, null, null, null, null, null, 20));
        assertThat(page.totalCount()).isEqualTo(1);
    }

    @Test
    void 포트폴리오와_구인글을_함께_요청하면_최신순으로_병합된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        String jobPostingId = publishedJobPosting(authorId, token + " 구인 공고");
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.SKETCH), List.of(Genre.DRAMA), AgeRating.ALL, token + " 포트폴리오");
        // 두 소스 모두 @ApplicationModuleListener(비동기) 색인이라, 둘 다 반영될 때까지 기다린다
        awaitCondition(() -> searchService.search(mergedQuery(token, 20)).items().size() == 2);

        SearchPage<SearchResultItem> page = searchService.search(mergedQuery(token, 20));

        assertThat(page.items()).extracting(SearchResultItem::id)
                .containsExactlyInAnyOrder(artwork.id(), jobPostingId);
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse();

        // 커서로 이어서 조회하면 나머지 한 건이 중복 없이 조회된다
        SearchPage<SearchResultItem> firstPage = searchService.search(mergedQuery(token, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.hasNext()).isTrue();

        SearchPage<SearchResultItem> secondPage = searchService.search(new SearchQuery(
                token, null, null, null, null, null, null, null, null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().get(0).id()).isNotEqualTo(firstPage.items().get(0).id());
    }

    @Test
    void 포트폴리오_전용_필터가_걸리면_구인글은_결과에서_제외된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        publishedJobPosting(authorId, token + " 구인 공고");

        // 작품 분야는 recruit 게시글에 없는 속성이라 축 간 AND 결합상 결과가 없다
        SearchPage<SearchResultItem> page = searchService.search(new SearchQuery(
                token, List.of(PostType.JOB_POSTING), List.of(ArtworkField.WEBTOON), null, null, null, null, null,
                null, null, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void recruit_승인되지_않은_구인글은_검색에서_제외된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        // 커맨드의 submit=true라 저장 즉시 PENDING이며, 공개 검색 대상이 아니다
        JobPostingInfo pending = recruitService.createJobPosting(authorId, jobPostingCommand(token + " 미공개 공고"));

        SearchPage<SearchResultItem> beforeApproval = searchService.search(recruitQuery(token, 20));
        assertThat(beforeApproval.items()).isEmpty();

        String publishedId = recruitService.approveJobPosting(pending.id()).id();

        List<SearchResultItem> found = awaitSearchResult(() -> searchService.search(recruitQuery(token, 20)));
        assertThat(found).extracting(SearchResultItem::id).containsExactly(publishedId);
    }

    @Test
    void recruit_장르_태그_필터가_적용된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        // 장르가 정본 enum이라 토큰으로 유일한 장르를 만들 수 없다 — 검색어(token)로 이번 테스트가 만든
        // 두 글로 범위를 좁힌 뒤, 서로 다른 장르 중 하나로 필터가 걸리는지 검증한다.
        String teamPostingId = recruitService
                .createTeamPosting(authorId, teamPostingCommand(token + " 팀원 모집", Genre.ACTION)).id();
        publishedJobPosting(authorId, token + " 구인 공고"); // 장르가 다른(ROMANCE_FANTASY) 구인글 — 걸리지 않아야 한다

        List<SearchResultItem> byGenre = awaitSearchResult(() -> searchService.search(new SearchQuery(
                token, null, null, null, null, null, List.of(Genre.ACTION), null, null, null, 20)));

        assertThat(byGenre).extracting(SearchResultItem::id).containsExactly(teamPostingId);
    }

    @Test
    void recruit_검색_결과는_커서로_이어서_조회되고_hasNext와_totalCount가_정확하다() {
        String token = uniqueToken();
        String authorId = registerMember();
        String firstId = publishedJobPosting(authorId, token + " 공고 1");
        String secondId = publishedJobPosting(authorId, token + " 공고 2");

        awaitCondition(() -> searchService.search(recruitQuery(token, 20)).items().size() == 2);

        SearchPage<SearchResultItem> fullPage = searchService.search(recruitQuery(token, 20));
        assertThat(fullPage.totalCount()).isEqualTo(2);
        assertThat(fullPage.hasNext()).isFalse();

        SearchPage<SearchResultItem> firstPage = searchService.search(recruitQuery(token, 1));
        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.totalCount()).isEqualTo(2);

        SearchPage<SearchResultItem> secondPage = searchService.search(new SearchQuery(
                token, List.of(PostType.JOB_POSTING, PostType.JOB_SEEKING, PostType.TEAM_RECRUIT),
                null, null, null, null, null, null, null, firstPage.nextCursor(), 1));
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(List.of(firstPage.items().get(0).id(), secondPage.items().get(0).id()))
                .containsExactlyInAnyOrder(firstId, secondId);
    }

    @Test
    void recruit_전체_재색인_후에도_기존_구인글이_검색된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        String jobPostingId = publishedJobPosting(authorId, token + " 재색인 공고");
        awaitSearchResult(() -> searchService.search(recruitQuery(token, 20)));

        // alias(recruit_posts)를 새 물리 인덱스로 원자적으로 전환 — docs/design/search-module-design.md §5.3
        recruitReindexService.reindexAll();

        SearchPage<SearchResultItem> found = searchService.search(recruitQuery(token, 20));
        assertThat(found.items()).extracting(SearchResultItem::id).containsExactly(jobPostingId);
    }

    private SearchQuery recruitQuery(String q, int size) {
        return new SearchQuery(q, List.of(PostType.JOB_POSTING, PostType.JOB_SEEKING, PostType.TEAM_RECRUIT),
                null, null, null, null, null, null, null, null, size);
    }

    private CreateTeamPostingCommand teamPostingCommand(String title, Genre genre) {
        return new CreateTeamPostingCommand(
                title, false, false, false, "팀장", "010-0000-0000", "팀 소개",
                List.of("공모전"), TeamWorkLocationType.ONLINE, null,
                List.of(ArtworkRole.BACKGROUND), List.of(genre), false, true, null, null, 3, "포트폴리오 심사",
                TeamActivityDuration.THREE_MONTHS, TeamWeeklyActivityTime.TWO_TO_THREE_TIMES,
                "프로젝트 소개", "https://img.example/team.png", List.of());
    }

    @Test
    void 검색어와_필터가_모두_없으면_빈_결과를_반환한다() {
        SearchPage<SearchResultItem> result = searchService.search(new SearchQuery(
                null, null, null, null, null, null, null, null, null, null, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void 비공개로_전환하면_색인에서_제거된다() {
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.PRINT_COMIC, CreativeType.FAN_ART,
                List.of(ArtworkRole.COLORING), List.of(Genre.ACTION), AgeRating.ALL);

        awaitSearchResult(() -> searchService.search(queryWithArtworkField(ArtworkField.PRINT_COMIC)));

        artworkService.updateVisibility(artwork.authorId(), artwork.id(), Visibility.PRIVATE);

        awaitCondition(() -> {
            SearchPage<SearchResultItem> page = searchService.search(queryWithArtworkField(ArtworkField.PRINT_COMIC));
            return page.items().stream().noneMatch(i -> i.id().equals(artwork.id()));
        });
    }

    private SearchQuery queryWithArtworkField(ArtworkField field) {
        return new SearchQuery(null, null, List.of(field), null, null, null, null, null, null, null, 20);
    }

    // 포트폴리오와 recruit 소유 유형을 함께 요청하는 질의(유형 필터 미지정 = 전체 유형)
    private SearchQuery mergedQuery(String q, int size) {
        return new SearchQuery(q, null, null, null, null, null, null, null, null, null, size);
    }

    // 같은 DB/색인을 공유하는 다른 테스트와 겹치지 않는 검색 토큰
    private String uniqueToken() {
        return "token" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String registerMember() {
        String memberId = memberService.register(uniqueEmail(), uniqueHandle(), "검색테스트작가",
                CreatorRole.WEBTOON).id();
        // 구인글·팀원모집글은 유료 단건 게시 상품이다(구인구직-R02).
        BillingTestSupport.grantAllPostingProducts(balanceRepository, memberId);
        return memberId;
    }

    // 작성 → 관리자 승인까지 마친 PUBLISHED 구인글 ID를 반환한다(커맨드의 submit=true라 저장 즉시 PENDING).
    private String publishedJobPosting(String authorMemberId, String title) {
        JobPostingInfo created = recruitService.createJobPosting(authorMemberId, jobPostingCommand(title));
        return recruitService.approveJobPosting(created.id()).id();
    }

    // submit=true라 저장 즉시 PENDING — 검색 노출 전 상태를 검증할 때는 approveJobPosting을 호출하지 않는다.
    private CreateJobPostingCommand jobPostingCommand(String title) {
        return new CreateJobPostingCommand(
                title, "앳크루", "대표", "웹툰", "서울", "02-000-0000", "https://example.com",
                "회사 소개", true, true, false,
                List.of(ArtworkRole.TOTAL_ARTWORK), List.of(Genre.ROMANCE_FANTASY), "작업 범위", null, 2, "서류 → 면접",
                "무관", "신입", "무관", "무관",
                JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE, JobWorkScheduleType.FIXED,
                null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 3000L, 4000L, true,
                null, null, false, "복지 설명", List.of("식대"),
                "https://img.example/thumb.png", List.of("https://img.example/ref.png"), true);
    }

    private ArtworkInfo uploadReadyArtwork(ArtworkField field, CreativeType creativeType,
                                            List<ArtworkRole> roles, List<Genre> genres, AgeRating ageRating) {
        return uploadReadyArtwork(field, creativeType, roles, genres, ageRating,
                "검색테스트 작품 " + Instant.now().toEpochMilli());
    }

    private ArtworkInfo uploadReadyArtwork(ArtworkField field, CreativeType creativeType,
                                            List<ArtworkRole> roles, List<Genre> genres, AgeRating ageRating,
                                            String title) {
        String memberId = registerMember();

        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                title, "설명",
                field, creativeType, roles, genres, List.of("태그"),
                ageRating, Visibility.PUBLIC, List.of(), null, null, List.of(), List.of()
        ));

        // media webhook → MediaAssetProcessedEvent → artwork 리스너(비동기)로 READY 전환된다.
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artwork.id(), imageKeys.get(0),
                "thumb-key", null, "orig.avif", MediaProcessingStatus.DONE);
        awaitCondition(() -> artworkService.getArtworkStatus(memberId, artwork.id()) == ArtworkStatus.READY);

        return artwork;
    }

    /** ArtworkSearchIndexer의 @ApplicationModuleListener는 비동기라, 색인 반영까지 폴링한다. */
    private List<SearchResultItem> awaitSearchResult(Supplier<SearchPage<SearchResultItem>> query) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            SearchPage<SearchResultItem> page = query.get();
            if (!page.items().isEmpty()) {
                return page.items();
            }
            sleep();
        }
        throw new AssertionError("색인 반영 대기 시간 초과");
    }

    private void awaitCondition(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) return;
            sleep();
        }
        throw new AssertionError("조건 충족 대기 시간 초과");
    }

    private void sleep() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String uniqueEmail() {
        return "search-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@test.com";
    }

    private String uniqueHandle() {
        return "search" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
