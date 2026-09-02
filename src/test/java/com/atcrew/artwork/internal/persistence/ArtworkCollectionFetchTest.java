package com.atcrew.artwork.internal.persistence;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.member.Language;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작품 목록 조회의 쿼리 수 회귀 방지 (이슈 #112).
 *
 * <p>배경: {@code Artwork}는 {@code FetchType.EAGER} 컬렉션을 8개 가진다. Hibernate는 컬렉션을
 * 하나만 조인 페치할 수 있어 나머지는 엔티티마다 개별 SELECT를 던진다 — 항목 20개면 컬렉션 로딩만
 * 160회다. 2026-09-02 부하 측정에서 {@code /api/community/artworks?size=20}이 요청당 SELECT를
 * 189회 던지고, 그 탓에 처리량이 약 15 RPS에서 꺾이는 것을 실측했다
 * (docs/operations/baseline/2026-09-02-real-data-load-test.md).
 *
 * <p>{@code @BatchSize}가 붙으면 컬렉션당 1회의 IN 조회로 묶인다. 이 테스트는 그 상태를 고정한다 —
 * 컬렉션이 추가되거나 {@code @BatchSize}가 빠지면 쿼리 수가 다시 항목 수에 비례해 늘어난다.
 *
 * <p>임계값은 여유를 두고 잡았다. 정확한 수치를 박으면 컬렉션이 하나 늘 때마다 무관한 실패가 난다 —
 * "항목 수에 비례하는가"만 갈리면 목적을 달성한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ArtworkCollectionFetchTest {

    private static final int PAGE_SIZE = 20;

    /** 루트 조회 1회 + 컬렉션 8회를 기준으로 잡은 상한. {@code @BatchSize}가 없으면 160회를 넘는다. */
    private static final int MAX_STATEMENTS = 20;

    @Autowired
    ArtworkRepository artworkRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void 페이지_크기보다_많은_작품을_저장한다() {
        artworkRepository.deleteAll();
        for (int i = 0; i < PAGE_SIZE + 5; i++) {
            artworkRepository.save(artworkWithCollections("author-" + i));
        }
    }

    @Test
    void 목록_조회_쿼리_수가_항목_수에_비례하지_않는다() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        List<Artwork> page = artworkRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, PAGE_SIZE));
        // 컬렉션을 실제로 읽어야 로딩이 일어난다 — 엔티티 개수만 세면 검증이 무의미하다.
        page.forEach(artwork -> {
            artwork.getImages().size();
            artwork.getRoles().size();
            artwork.getGenres().size();
            artwork.getCustomTags().size();
            artwork.getTags().size();
            artwork.getTools().size();
            artwork.getLanguages().size();
            artwork.getMaterials().size();
        });

        assertThat(page).hasSize(PAGE_SIZE);
        assertThat(stats.getPrepareStatementCount())
                .as("항목 %d개 조회에 실행된 SQL 수 — @BatchSize가 빠지면 컬렉션 8개 × %d개로 급증한다",
                        PAGE_SIZE, PAGE_SIZE)
                .isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    private Artwork artworkWithCollections(String authorId) {
        return Artwork.create(
                authorId, "쿼리 수 측정용 작품", "설명",
                List.of("raw/1.png"), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.values()[0]), List.of(Genre.values()[0]), null,
                List.of("태그"), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of("도구"), null, null, List.of(), List.of());
    }
}
