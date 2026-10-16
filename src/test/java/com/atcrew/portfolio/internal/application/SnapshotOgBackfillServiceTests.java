package com.atcrew.portfolio.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.media.MediaAssetSpec;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OG 백필 2단계 — 썸네일 key만 얼려 둔 기존 고정형 스냅샷에 media가 만든 OG를 얼린 등급으로 골라 채운다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class SnapshotOgBackfillServiceTests {

    @Autowired SnapshotOgBackfillService backfillService;
    @Autowired PortfolioRepository portfolioRepository;
    @Autowired PortfolioItemSnapshotRepository snapshotRepository;
    @Autowired MediaService mediaService;
    @Autowired MediaCallbackService callbackService;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void 얼린_등급으로_og를_골라_채우고_자산이_없으면_unresolved로_센다() {
        long start = backfillService.backfill(1000, 0L).lastId();
        String portfolioId = snapshotPortfolio();
        String name = UUID.randomUUID().toString();
        thumbnailAssetWithOg(name);
        Long adult = snapshotRepository.save(snapshot(portfolioId, 0, "thumb/" + name + ".avif", AgeRating.R18)).getId();
        Long gone = snapshotRepository.save(snapshot(portfolioId, 1, "thumb/gone-" + name + ".avif", AgeRating.ALL)).getId();

        SnapshotOgBackfillService.Result result = backfillService.backfill(1000, start);

        assertThat(result.filled()).isEqualTo(1);
        assertThat(result.unresolved()).isEqualTo(1);
        transactionTemplate.executeWithoutResult(status -> {
            PortfolioItemSnapshot filled = snapshotRepository.findById(adult).orElseThrow();
            assertThat(filled.getOgKey()).isEqualTo("og-adult/" + name + ".jpg");
            assertThat(snapshotRepository.findActiveReferencedKeys(List.of("og-adult/" + name + ".jpg")))
                    .containsExactly("og-adult/" + name + ".jpg");
            assertThat(snapshotRepository.findById(gone).orElseThrow().getOgKey()).isNull();
        });
    }

    @Test
    void 다시_실행하면_이미_채운_행은_건드리지_않는다() {
        long start = backfillService.backfill(1000, 0L).lastId();
        String portfolioId = snapshotPortfolio();
        String name = UUID.randomUUID().toString();
        thumbnailAssetWithOg(name);
        snapshotRepository.save(snapshot(portfolioId, 0, "thumb/" + name + ".avif", AgeRating.ALL));

        assertThat(backfillService.backfill(1000, start).filled()).isEqualTo(1);
        assertThat(backfillService.backfill(1000, start).filled()).isZero();
    }

    private void thumbnailAssetWithOg(String name) {
        String ownerId = UUID.randomUUID().toString();
        String rawKey = "raw/" + name + ".png";
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, List.of(MediaAssetSpec.of(rawKey)),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, rawKey, "thumb/" + name + ".avif",
                "thumb-adult/" + name + ".avif", null, "og/" + name + ".jpg", "og-adult/" + name + ".jpg",
                MediaProcessingStatus.DONE, null);
    }

    private String snapshotPortfolio() {
        return portfolioRepository.save(Portfolio.createShared(UUID.randomUUID().toString(), ReflectionType.SNAPSHOT,
                "고정형", UUID.randomUUID().toString().replace("-", "").substring(0, 6), null)).getId();  // 6자 공개 코드, slug 없음
    }

    private static PortfolioItemSnapshot snapshot(String portfolioId, int ordinal, String thumbKey, AgeRating ageRating) {
        return PortfolioItemSnapshot.of(portfolioId, ordinal, UUID.randomUUID().toString(), "작품", thumbKey, null,
                null, ageRating, ArtworkField.ILLUSTRATION, Instant.now(), "{}", List.of(thumbKey));
    }
}
