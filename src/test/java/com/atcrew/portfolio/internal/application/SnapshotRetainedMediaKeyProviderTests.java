package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 활성 고정형 스냅샷의 R2 key 보존 판정 검증 (docs/design/portfolio-module-design.md §5.6).
 *
 * <p>스냅샷은 원본 key를 그대로 참조하므로, 원본 영구 삭제·이미지 교체로 media가 정리하려는 key 중
 * 스냅샷이 쓰는 key를 정확히 골라내야 한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class SnapshotRetainedMediaKeyProviderTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    SnapshotRetainedMediaKeyProvider provider;

    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    PortfolioItemSnapshotRepository snapshotRepository;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void 스냅샷이_참조하는_썸네일과_상세_이미지_키를_보존_대상으로_돌려준다() {
        String portfolioId = givenSnapshotPortfolio();
        snapshotRepository.save(snapshotOf(portfolioId, "thumb/a.avif", "thumb-adult/a.avif",
                new ArtworkImageInfo("raw/a.png", "thumb/a.avif", "thumb-adult/a.avif", "raw/a.avif",
                        ImageProcessingStatus.DONE)));

        var retained = provider.retainedKeys(
                List.of("raw/a.png", "thumb/a.avif", "thumb-adult/a.avif", "raw/a.avif"));

        assertThat(retained)
                .containsExactlyInAnyOrder("raw/a.png", "thumb/a.avif", "thumb-adult/a.avif", "raw/a.avif");
    }

    @Test
    void 스냅샷과_무관한_키는_보존_대상이_아니다() {
        String portfolioId = givenSnapshotPortfolio();
        snapshotRepository.save(snapshotOf(portfolioId, "thumb/b.avif", null,
                new ArtworkImageInfo("raw/b.png", "thumb/b.avif", null, null, ImageProcessingStatus.DONE)));

        var retained = provider.retainedKeys(List.of("raw/b.png", "thumb/b.avif", "raw/other.png"));

        assertThat(retained).containsExactlyInAnyOrder("raw/b.png", "thumb/b.avif");
    }

    @Test
    void 포트폴리오가_삭제된_스냅샷_행은_보존_대상이_아니다() {
        // 포트폴리오 삭제 시 스냅샷도 함께 지우지만, 남은 행이 있어도 보존 판정에 걸리지 않아야 한다.
        String deletedPortfolioId = UUID.randomUUID().toString();
        snapshotRepository.save(snapshotOf(deletedPortfolioId, "thumb/c.avif", null,
                new ArtworkImageInfo("raw/c.png", "thumb/c.avif", null, null, ImageProcessingStatus.DONE)));

        var retained = provider.retainedKeys(List.of("raw/c.png", "thumb/c.avif"));

        assertThat(retained).isEmpty();
    }

    @Test
    void 후보_키가_비어_있으면_조회하지_않고_빈_집합을_돌려준다() {
        assertThat(provider.retainedKeys(List.of())).isEmpty();
    }

    private String givenSnapshotPortfolio() {
        Portfolio portfolio = portfolioRepository.save(Portfolio.createShared(
                UUID.randomUUID().toString(), ReflectionType.SNAPSHOT, "고정형", newSlug()));
        return portfolio.getId();
    }

    private PortfolioItemSnapshot snapshotOf(String portfolioId, String thumbKey, String thumbAdultKey,
                                             ArtworkImageInfo image) {
        ArtworkSnapshotPayload payload = new ArtworkSnapshotPayload(List.of(image), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), "본문", 0);
        return PortfolioItemSnapshot.of(portfolioId, 0, UUID.randomUUID().toString(), "작품",
                thumbKey, thumbAdultKey, AgeRating.ALL, ArtworkField.ILLUSTRATION, Instant.now(),
                jsonMapper.writeValueAsString(payload));
    }

    private String newSlug() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 22);
    }
}
