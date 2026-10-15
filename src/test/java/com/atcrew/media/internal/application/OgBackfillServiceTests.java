package com.atcrew.media.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.media.MediaAssetSpec;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OG 백필 대상 선정 — 카드 썸네일 자산(ARTWORK_THUMBNAIL)·처리 완료·썸네일 2종 있음·OG 없음만 고른다.
 * 예전 STANDARD 프로필이 본문 이미지에도 썸네일을 만들었으므로 "썸네일 있음"만으로 고르면 본문 이미지까지 처리한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class OgBackfillServiceTests {

    @Autowired MediaService mediaService;
    @Autowired MediaCallbackService callbackService;
    @Autowired OgBackfillService backfillService;
    @MockitoBean ImageProcessingWorker worker;

    @Test
    void 카드_썸네일_자산_중_og가_없는_처리_완료_행만_트리거한다() {
        String target = thumbnail("raw/target.png", null);
        thumbnail("raw/done.png", "og/done.jpg");
        String body = UUID.randomUUID().toString();
        mediaService.syncAssets(MediaOwnerType.ARTWORK, body, List.of(MediaAssetSpec.of("raw/body.png")),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK, body, "raw/body.png", "thumb/body.avif", "thumb-adult/body.avif",
                "original/body.avif", MediaProcessingStatus.DONE);
        String pending = UUID.randomUUID().toString();
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, pending, List.of(MediaAssetSpec.of("raw/pending.png")),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);

        OgBackfillService.Result result = backfillService.backfill(1000, 0L);

        assertThat(result.remaining()).isZero();
        verify(worker).triggerOgAsync(MediaOwnerType.ARTWORK_THUMBNAIL, target, "raw/target.png",
                "thumb/target.avif", "thumb-adult/target.avif");
        verify(worker, never()).triggerOgAsync(any(), eq(body), any(), any(), any());
        verify(worker, never()).triggerOgAsync(any(), eq(pending), any(), any(), any());
    }

    // 콜백은 비동기라 직후 재호출하면 아직 og가 비어 있다 — afterId로 이어 읽어 같은 행을 두 번 보내지 않는다.
    @Test
    void limit만큼_보내고_남은_건수와_이어_읽을_id를_돌려준다() {
        // 같은 클래스의 다른 테스트가 남긴 대상 행을 건너뛰고 이 테스트가 만든 행부터 센다.
        long start = backfillService.backfill(1000, 0L).lastId();
        thumbnail("raw/a.png", null);
        thumbnail("raw/b.png", null);
        thumbnail("raw/c.png", null);

        OgBackfillService.Result first = backfillService.backfill(2, start);
        OgBackfillService.Result second = backfillService.backfill(2, first.lastId());

        assertThat(first.queued()).isEqualTo(2);
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.queued()).isEqualTo(1);
        assertThat(second.remaining()).isZero();
    }

    private String thumbnail(String rawKey, String ogKey) {
        String ownerId = UUID.randomUUID().toString();
        String base = rawKey.substring(rawKey.lastIndexOf('/') + 1, rawKey.lastIndexOf('.'));
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, List.of(MediaAssetSpec.of(rawKey)),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, rawKey, "thumb/" + base + ".avif",
                "thumb-adult/" + base + ".avif", null, ogKey, ogKey == null ? null : "og-adult/" + base + ".jpg",
                MediaProcessingStatus.DONE, null);
        return ownerId;
    }
}
