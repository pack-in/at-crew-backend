package com.atcrew.search;

import com.atcrew.TestMongoConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessedCallbackCommand;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
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
import com.atcrew.search.internal.application.ArtworkReindexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MongoDBContainer;
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
@Import(TestMongoConfig.class)
class SearchModuleTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

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

    @Test
    void 전체_재색인_후에도_기존_작품이_검색된다() {
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.ANIMATION, CreativeType.COMMISSION,
                List.of(ArtworkRole.BACKGROUND), List.of("SF"), AgeRating.ALL);
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
                List.of(ArtworkRole.LINEART), List.of("BL"), AgeRating.ALL);

        List<SearchResultItem> found = awaitSearchResult(
                () -> searchService.search(queryWithArtworkField(ArtworkField.ILLUSTRATION)));

        assertThat(found).extracting(SearchResultItem::id).contains(artwork.id());
    }

    @Test
    void 필터가_일치하지_않으면_결과에서_제외된다() {
        uploadReadyArtwork(ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of("판타지"), AgeRating.ALL);

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

        SearchPage<SearchResultItem> page = searchService.search(new SearchQuery(
                token, List.of(PostType.JOB_POSTING), null, null, null, null, null, null, null, null, 20));

        assertThat(page.items()).extracting(SearchResultItem::id).containsExactly(jobPostingId);
        assertThat(page.items()).extracting(SearchResultItem::postType).containsOnly(PostType.JOB_POSTING);
        assertThat(page.totalCount()).isEqualTo(1);
    }

    @Test
    void 포트폴리오와_구인글을_함께_요청하면_최신순으로_병합된다() {
        String token = uniqueToken();
        String authorId = registerMember();
        String jobPostingId = publishedJobPosting(authorId, token + " 구인 공고");
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.WEBTOON, CreativeType.ORIGINAL,
                List.of(ArtworkRole.SKETCH), List.of("드라마"), AgeRating.ALL, token + " 포트폴리오");
        // 구인글은 즉시 조회되지만 작품 색인은 비동기라, 두 소스가 모두 반영될 때까지 기다린다
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
    void 검색어와_필터가_모두_없으면_빈_결과를_반환한다() {
        SearchPage<SearchResultItem> result = searchService.search(new SearchQuery(
                null, null, null, null, null, null, null, null, null, null, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void 비공개로_전환하면_색인에서_제거된다() {
        ArtworkInfo artwork = uploadReadyArtwork(ArtworkField.PRINT_COMIC, CreativeType.FAN_ART,
                List.of(ArtworkRole.COLORING), List.of("액션"), AgeRating.ALL);

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
        return memberService.register(uniqueEmail(), uniqueHandle(), "검색테스트작가", CreatorRole.WEBTOON).id();
    }

    // 작성 → 관리자 승인까지 마친 PUBLISHED 구인글 ID를 반환한다(커맨드의 submit=true라 저장 즉시 PENDING).
    private String publishedJobPosting(String authorMemberId, String title) {
        JobPostingInfo created = recruitService.createJobPosting(authorMemberId, new CreateJobPostingCommand(
                title, "앳크루", "대표", "웹툰", "서울", "02-000-0000", "https://example.com",
                "회사 소개", true, true, false,
                List.of("작화"), List.of("로맨스"), "작업 범위", null, 2, "서류 → 면접",
                "무관", "신입", "무관", "무관",
                JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE, JobWorkScheduleType.FIXED,
                null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 3000L, 4000L, true,
                null, null, false, "복지 설명", List.of("식대"),
                "https://img.example/thumb.png", List.of("https://img.example/ref.png"), true));
        return recruitService.approveJobPosting(created.id()).id();
    }

    private ArtworkInfo uploadReadyArtwork(ArtworkField field, CreativeType creativeType,
                                            List<ArtworkRole> roles, List<String> genres, AgeRating ageRating) {
        return uploadReadyArtwork(field, creativeType, roles, genres, ageRating,
                "검색테스트 작품 " + Instant.now().toEpochMilli());
    }

    private ArtworkInfo uploadReadyArtwork(ArtworkField field, CreativeType creativeType,
                                            List<ArtworkRole> roles, List<String> genres, AgeRating ageRating,
                                            String title) {
        String memberId = registerMember();

        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                title, "설명",
                field, creativeType, roles, genres, List.of("태그"),
                ageRating, Visibility.PUBLIC, List.of(), null, null, List.of(), List.of()
        ));

        artworkService.handleImageProcessedCallback(new ImageProcessedCallbackCommand(
                artwork.id(), imageKeys.get(0), "thumb-key", null, "orig.avif", ImageProcessingStatus.DONE));

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
