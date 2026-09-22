package com.atcrew.media.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.media.MediaAssetInfo;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V43 백필 검증 — 도메인 이미지 자식 행이 {@code media_assets}로 들어오는지 확인한다. 테스트가 SQL을 따로
 * 적으면 실제 마이그레이션과 어긋나도 모르므로, 마이그레이션 파일의 INSERT를 그대로 읽어 실행한다(V41과 같은 방식).
 *
 * <p>백필은 이미 한 번 적용된 뒤라 여기서 다시 돌린다. 소유자 단위 NOT EXISTS 조건 덕분에 반복 실행이 안전하다는
 * 것도 함께 확인하는 셈이다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class MediaBackfillMigrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MediaService mediaService;

    @Test
    void media_행이_없는_작품_이미지는_상태와_변형본까지_그대로_옮겨진다() throws IOException {
        String artworkId = UUID.randomUUID().toString();
        insertArtworkImage(artworkId, 0, "raw/a.png", "thumb/a.avif", "thumb-adult/a.avif", "original/a.avif", "DONE");
        insertArtworkImage(artworkId, 1, "raw/b.png", null, null, null, "FAILED");

        runBackfill();

        List<MediaAssetInfo> assets = mediaService.getAssets(MediaOwnerType.ARTWORK, artworkId);
        assertThat(assets).extracting(MediaAssetInfo::originalKey).containsExactly("raw/a.png", "raw/b.png");
        assertThat(assets).extracting(MediaAssetInfo::status)
                .containsExactly(MediaProcessingStatus.DONE, MediaProcessingStatus.FAILED);
        assertThat(assets.get(0).thumbKey()).isEqualTo("thumb/a.avif");
        assertThat(assets.get(0).thumbAdultKey()).isEqualTo("thumb-adult/a.avif");
        assertThat(assets.get(0).originalAvifKey()).isEqualTo("original/a.avif");
        assertThat(assets.get(0).slotRole()).isNull();
        assertThat(queryOne("SELECT variant_profile FROM media_assets WHERE owner_id = ? AND ordinal = 0", artworkId))
                .isEqualTo(MediaVariantProfile.STANDARD_WITH_ADULT_BLUR.name());
        assertThat(queryOne("SELECT quality_tier FROM media_assets WHERE owner_id = ? AND ordinal = 0", artworkId))
                .isEqualTo(MediaQualityTier.ORIGINAL.name());
    }

    // media 행이 있는 소유자는 이미 media 경로로 등록된 것이다. 행 단위로 채우면 ordinal이 겹쳐 유니크 제약에 걸린다.
    @Test
    void media_행이_이미_있는_작품은_건드리지_않는다() throws IOException {
        String artworkId = UUID.randomUUID().toString();
        mediaService.syncAssets(MediaOwnerType.ARTWORK, artworkId, List.of(com.atcrew.media.MediaAssetSpec.of("raw/new.png")),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);
        insertArtworkImage(artworkId, 0, "raw/old.png", null, null, null, "DONE");

        runBackfill();

        assertThat(mediaService.getAssets(MediaOwnerType.ARTWORK, artworkId))
                .extracting(MediaAssetInfo::originalKey).containsExactly("raw/new.png");
    }

    @Test
    void 구인글_이미지는_슬롯_이름까지_옮겨진다() throws IOException {
        String postingId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO job_posting_images (posting_id, role, ordinal, original_key, thumb_key,
                    original_avif_key, processing_status)
                VALUES (?, 'THUMBNAIL', 0, 'raw/t.png', 'thumb/t.avif', 'original/t.avif', 'DONE'),
                       (?, 'REFERENCE', 1, 'raw/r.png', NULL, NULL, 'PENDING')
                """, postingId, postingId);

        runBackfill();

        List<MediaAssetInfo> assets = mediaService.getAssets(MediaOwnerType.JOB_POSTING, postingId);
        assertThat(assets).extracting(MediaAssetInfo::slotRole).containsExactly("THUMBNAIL", "REFERENCE");
        assertThat(assets).extracting(MediaAssetInfo::originalKey).containsExactly("raw/t.png", "raw/r.png");
        assertThat(assets.get(0).thumbAdultKey()).isNull();
        assertThat(queryOne("SELECT quality_tier FROM media_assets WHERE owner_id = ? AND ordinal = 0", postingId))
                .isEqualTo(MediaQualityTier.WEB.name());
    }

    private void insertArtworkImage(String artworkId, int ordinal, String originalKey, String thumbKey,
                                    String thumbAdultKey, String originalAvifKey, String status) {
        jdbcTemplate.update("""
                INSERT INTO artwork_images (artwork_id, ordinal, original_key, thumb_key, thumb_adult_key,
                    original_avif_key, processing_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, artworkId, ordinal, originalKey, thumbKey, thumbAdultKey, originalAvifKey, status);
    }

    private String queryOne(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    /** V43 파일의 INSERT 문들 — 실제 마이그레이션과 어긋나지 않도록 파일에서 읽어 그대로 실행한다. */
    private void runBackfill() throws IOException {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V43__backfill_media_assets_from_domain_images.sql"));
        Arrays.stream(migration.substring(migration.indexOf("INSERT INTO")).split(";"))
                .map(String::trim)
                .filter(statement -> statement.startsWith("INSERT INTO"))
                .forEach(jdbcTemplate::execute);
    }
}
