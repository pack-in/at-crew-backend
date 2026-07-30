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
import com.atcrew.search.internal.application.ArtworkReindexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
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
 * <p>search는 artwork에 의존(SearchServiceImpl, ArtworkSearchIndexer)하므로 DIRECT_DEPENDENCIES로는
 * artwork의 추이적 의존성(memberService 등)까지 부트스트랩되지 않는다 — community 모듈 테스트와 동일한 이유로
 * ALL_DEPENDENCIES를 사용한다.
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

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    SearchService searchService;

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

    private ArtworkInfo uploadReadyArtwork(ArtworkField field, CreativeType creativeType,
                                            List<ArtworkRole> roles, List<String> genres, AgeRating ageRating) {
        String memberId = memberService.register(
                uniqueEmail(), uniqueHandle(), "검색테스트작가", CreatorRole.WEBTOON).id();

        List<String> imageKeys = List.of("raw/" + UUID.randomUUID() + ".png");
        ArtworkInfo artwork = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                imageKeys, 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "검색테스트 작품 " + Instant.now().toEpochMilli(), "설명",
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
