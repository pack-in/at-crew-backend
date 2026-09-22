package com.atcrew.portfolio.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
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
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class SnapshotRetainedMediaKeyProviderTests {

    @Autowired
    SnapshotRetainedMediaKeyProvider provider;

    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    PortfolioItemSnapshotRepository snapshotRepository;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    // 고아 행은 이미지 한 장의 변형본만 담기도 한다 — 썸네일이 후보에 없어도 스냅샷이 참조하는 key는 보존한다.
    @Test
    void 후보에_썸네일이_없어도_스냅샷이_참조하는_상세_이미지_키를_보존한다() {
        String portfolioId = givenSnapshotPortfolio();
        snapshotRepository.save(snapshotOf(portfolioId, "thumb/e1.avif", null,
                new ArtworkImageInfo("raw/e1.png", "thumb/e1.avif", null, "original/e1.avif", ImageProcessingStatus.DONE),
                new ArtworkImageInfo("raw/e2.png", "thumb/e2.avif", "thumb-adult/e2.avif", "original/e2.avif",
                        ImageProcessingStatus.DONE)));

        var retained = provider.retainedKeys(List.of("thumb/e2.avif", "thumb-adult/e2.avif", "original/e2.avif"));

        assertThat(retained).containsExactlyInAnyOrder("thumb/e2.avif", "thumb-adult/e2.avif", "original/e2.avif");
    }

    // 사용자 지정 썸네일을 쓴 스냅샷은 카드 thumb_key가 지정 썸네일이다. media 자산 행에서 나온 후보에는 그 key가
    // 없다 — 예전 판정은 여기서 스냅샷을 못 찾아 상세 이미지를 지웠다.
    @Test
    void 사용자_지정_썸네일을_쓴_스냅샷도_자산_키만으로_보존한다() {
        String portfolioId = givenSnapshotPortfolio();
        snapshotRepository.save(snapshotOf(portfolioId, "raw/custom-thumb.png", null,
                new ArtworkImageInfo("raw/f.png", "thumb/f.avif", "thumb-adult/f.avif", "original/f.avif",
                        ImageProcessingStatus.DONE)));

        var retained = provider.retainedKeys(List.of("raw/f.png", "thumb/f.avif", "thumb-adult/f.avif", "original/f.avif"));

        assertThat(retained).containsExactlyInAnyOrder("raw/f.png", "thumb/f.avif", "thumb-adult/f.avif", "original/f.avif");
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

    // V41 이전에 만들어진 스냅샷은 색인이 없다 — 마이그레이션의 채우기 SQL이 payload의 key를 빠짐없이 옮기는지 본다.
    @Test
    void V41_채우기_SQL은_기존_스냅샷의_key를_모두_색인에_넣는다() throws Exception {
        String portfolioId = givenSnapshotPortfolio();
        String payload = """
                {"images":[{"originalKey":"raw/g.png","thumbKey":"thumb/g.avif","thumbAdultKey":null,
                "originalAvifKey":"original/g.avif","processingStatus":"DONE"}],
                "materials":[{"name":"소재","attachmentKeys":["raw/att-g.png"]}],"description":"본문"}
                """;
        jdbcTemplate.update("""
                INSERT INTO portfolio_item_snapshots (portfolio_id, ordinal, source_artwork_id, snapshot_public_id,
                    title, thumb_key, thumb_adult_key, payload_json)
                VALUES (?, 0, ?, ?, '옛 작품', 'raw/custom-g.png', NULL, ?)
                """, portfolioId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), payload);
        Long snapshotId = jdbcTemplate.queryForObject(
                "SELECT id FROM portfolio_item_snapshots WHERE portfolio_id = ?", Long.class, portfolioId);

        jdbcTemplate.execute(backfillSql());

        assertThat(jdbcTemplate.queryForList(
                "SELECT media_key FROM portfolio_snapshot_media_keys WHERE snapshot_id = ?", String.class, snapshotId))
                .containsExactlyInAnyOrder("raw/custom-g.png", "raw/g.png", "thumb/g.avif", "original/g.avif");
        // 자료 첨부 key는 소유 검증 없는 입력이라(#190) 색인하지 않는다 — 남의 파일 삭제를 막는 데 쓰일 수 있다.
        assertThat(provider.retainedKeys(List.of("raw/att-g.png"))).isEmpty();
        assertThat(provider.retainedKeys(List.of("original/g.avif"))).containsExactly("original/g.avif");
    }

    /** V41 파일의 채우기 INSERT 문 — 테스트가 따로 SQL을 적으면 실제 마이그레이션과 어긋나도 모른다. */
    private static String backfillSql() throws java.io.IOException {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V41__portfolio_snapshot_media_keys.sql"));
        String insert = migration.substring(migration.indexOf("INSERT IGNORE INTO"));
        return insert.substring(0, insert.lastIndexOf(';'));
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

    // 운영 경로(PortfolioServiceImpl.toSnapshot)와 같은 메서드로 색인 key를 만든다.
    private PortfolioItemSnapshot snapshotOf(String portfolioId, String thumbKey, String thumbAdultKey,
                                             ArtworkImageInfo... images) {
        ArtworkSnapshotPayload payload = new ArtworkSnapshotPayload(List.of(images), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), "본문", 0);
        return PortfolioItemSnapshot.of(portfolioId, 0, UUID.randomUUID().toString(), "작품",
                thumbKey, thumbAdultKey, AgeRating.ALL, ArtworkField.ILLUSTRATION, Instant.now(),
                jsonMapper.writeValueAsString(payload), payload.referencedMediaKeys(thumbKey, thumbAdultKey));
    }

    private String newSlug() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 22);
    }
}
