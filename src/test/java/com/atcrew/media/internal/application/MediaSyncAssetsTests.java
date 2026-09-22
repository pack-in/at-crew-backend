package com.atcrew.media.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.media.MediaAssetInfo;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB로 확인하는 {@link MediaService#syncAssets} 동작 — 단위 테스트(mock)로는 잡히지 않는
 * {@code uk_ma_owner_order} 유니크 제약 충돌을 여기서 검증한다(순서를 바꾸거나 앞의 이미지를 빼면
 * 유지되는 행의 ordinal이 서로 자리를 넘겨받아야 한다).
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class MediaSyncAssetsTests {

    @Autowired
    MediaService mediaService;

    @Test
    void 순서를_뒤집어도_유니크_제약에_걸리지_않고_행을_유지한다() {
        String ownerId = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId, specs("raw/a.png", "raw/b.png", "raw/c.png"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);

        var result = mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId,
                specs("raw/c.png", "raw/b.png", "raw/a.png"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);

        assertThat(result).extracting(MediaAssetInfo::originalKey)
                .containsExactly("raw/c.png", "raw/b.png", "raw/a.png");
        assertThat(result).extracting(MediaAssetInfo::ordinal).containsExactly(0, 1, 2);
        assertThat(mediaService.getAssets(MediaOwnerType.ARTWORK, ownerId))
                .extracting(MediaAssetInfo::originalKey)
                .containsExactly("raw/c.png", "raw/b.png", "raw/a.png");
    }

    @Test
    void 앞의_이미지를_빼면_뒤의_이미지가_자리를_넘겨받는다() {
        String ownerId = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId, specs("raw/a.png", "raw/b.png"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);

        var result = mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId, specs("raw/b.png"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);

        assertThat(result).extracting(MediaAssetInfo::originalKey).containsExactly("raw/b.png");
        assertThat(result).extracting(MediaAssetInfo::ordinal).containsExactly(0);
    }

    @Test
    void 슬롯_이름과_상태가_저장되고_일괄_조회로_읽힌다() {
        String first = ownerId();
        String second = ownerId();
        mediaService.syncAssets(MediaOwnerType.JOB_POSTING, first,
                List.of(new MediaAssetSpec("raw/t.png", "THUMBNAIL"), new MediaAssetSpec("raw/r.png", "REFERENCE")),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
        mediaService.syncAssets(MediaOwnerType.JOB_POSTING, second, specs("raw/s.png"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);

        var byOwner = mediaService.getAssets(MediaOwnerType.JOB_POSTING, List.of(first, second, ownerId()));

        assertThat(byOwner).hasSize(2);
        assertThat(byOwner.get(first)).extracting(MediaAssetInfo::slotRole).containsExactly("THUMBNAIL", "REFERENCE");
        assertThat(byOwner.get(first)).extracting(MediaAssetInfo::status)
                .containsOnly(MediaProcessingStatus.PENDING);
        assertThat(byOwner.get(second)).extracting(MediaAssetInfo::originalKey).containsExactly("raw/s.png");
    }

    private static List<MediaAssetSpec> specs(String... keys) {
        return List.of(keys).stream().map(MediaAssetSpec::of).toList();
    }

    private static String ownerId() {
        return UUID.randomUUID().toString();
    }
}
