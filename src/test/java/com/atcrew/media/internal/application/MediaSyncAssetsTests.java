package com.atcrew.media.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaAssetSpec;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
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

    @Autowired
    MediaCallbackService callbackService;

    @Autowired
    OrphanedMediaKeyRepository orphans;

    @Test
    void 순서를_뒤집어도_유니크_제약에_걸리지_않고_행을_유지한다() {
        String ownerId = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId, specs("raw/a.png", "raw/b.png", "raw/c.png"),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.ORIGINAL);

        var result = mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId,
                specs("raw/c.png", "raw/b.png", "raw/a.png"),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.ORIGINAL);

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
                MediaVariantProfile.ORIGINAL, MediaQualityTier.ORIGINAL);

        var result = mediaService.syncAssets(MediaOwnerType.ARTWORK, ownerId, specs("raw/b.png"),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.ORIGINAL);

        assertThat(result).extracting(MediaAssetInfo::originalKey).containsExactly("raw/b.png");
        assertThat(result).extracting(MediaAssetInfo::ordinal).containsExactly(0);
    }

    @Test
    void 슬롯_이름과_상태가_저장되고_일괄_조회로_읽힌다() {
        String first = ownerId();
        String second = ownerId();
        mediaService.syncAssets(MediaOwnerType.JOB_POSTING, first,
                List.of(new MediaAssetSpec("raw/t.png", "THUMBNAIL"), new MediaAssetSpec("raw/r.png", "REFERENCE")),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.WEB);
        mediaService.syncAssets(MediaOwnerType.JOB_POSTING, second, specs("raw/s.png"),
                MediaVariantProfile.ORIGINAL, MediaQualityTier.WEB);

        var byOwner = mediaService.getAssets(MediaOwnerType.JOB_POSTING, List.of(first, second, ownerId()));

        assertThat(byOwner).hasSize(2);
        assertThat(byOwner.get(first)).extracting(MediaAssetInfo::slotRole).containsExactly("THUMBNAIL", "REFERENCE");
        assertThat(byOwner.get(first)).extracting(MediaAssetInfo::status)
                .containsOnly(MediaProcessingStatus.PENDING);
        assertThat(byOwner.get(second)).extracting(MediaAssetInfo::originalKey).containsExactly("raw/s.png");
    }

    // 썸네일을 교체하면 예전 자산 행이 지워진다 — 그 행의 OG 파일도 고아 정리로 넘겨야 R2에 남지 않는다.
    @Test
    void 썸네일을_교체하면_예전_og_파일도_고아_정리_대상에_넣는다() {
        String ownerId = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, specs("raw/old.png"),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, "raw/old.png", "thumb/old.avif",
                "thumb-adult/old.avif", null, "og/old.jpg", "og-adult/old.jpg", MediaProcessingStatus.DONE, null);

        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, specs("raw/new.png"),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);

        assertThat(orphans.findAll()).flatExtracting(OrphanedMediaKey::getKeys)
                .contains("og/old.jpg", "og-adult/old.jpg", "thumb/old.avif");
    }

    @Test
    void og_key가_자산_조회_결과에_담긴다() {
        String ownerId = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, specs("raw/t.png"),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId, "raw/t.png", "thumb/t.avif",
                "thumb-adult/t.avif", null, "og/t.jpg", "og-adult/t.jpg", MediaProcessingStatus.DONE, null);

        assertThat(mediaService.getAssets(MediaOwnerType.ARTWORK_THUMBNAIL, ownerId))
                .extracting(MediaAssetInfo::ogKey, MediaAssetInfo::ogAdultKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("og/t.jpg", "og-adult/t.jpg"));
    }

    @Test
    void 썸네일_key로_og_key를_찾고_og가_없는_자산은_뺀다() {
        String withOg = ownerId();
        String withoutOg = ownerId();
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, withOg, specs("raw/w.png"),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, withOg, "raw/w.png", "thumb/w.avif",
                "thumb-adult/w.avif", null, "og/w.jpg", "og-adult/w.jpg", MediaProcessingStatus.DONE, null);
        mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, withoutOg, specs("raw/n.png"),
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        callbackService.process(MediaOwnerType.ARTWORK_THUMBNAIL, withoutOg, "raw/n.png", "thumb/n.avif",
                "thumb-adult/n.avif", null, MediaProcessingStatus.DONE);

        var found = mediaService.findOgKeysByThumbKeys(List.of("thumb/w.avif", "thumb/n.avif", "thumb/gone.avif"));

        assertThat(found).containsOnlyKeys("thumb/w.avif");
        assertThat(found.get("thumb/w.avif").ogAdultKey()).isEqualTo("og-adult/w.jpg");
    }

    private static List<MediaAssetSpec> specs(String... keys) {
        return List.of(keys).stream().map(MediaAssetSpec::of).toList();
    }

    private static String ownerId() {
        return UUID.randomUUID().toString();
    }
}
