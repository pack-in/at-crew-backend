package com.atcrew.artwork.internal.persistence;

import com.atcrew.SharedContainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V53__artwork_portfolio_public_code.sql 마이그레이션 검증 (docs/design/member-handle-design.md §3.1, §4.2-4, §5).
 *
 * <p>공유 MariaDB 컨테이너에 테스트마다 별도 데이터베이스를 만들고 V52까지 적용 → 픽스처 insert → V53 적용 순으로
 * 실제 운영 배포 순서를 재현한다. 코드는 RAND()라 정확한 값 대신 형식·불변식으로 검증한다. V52는 이 브랜치의
 * 원장 마이그레이션이라 항상 존재하므로, 병렬 브랜치의 V51 병합 여부와 무관하게 target 52 → 53이 성립한다.
 */
class PublicCodeMigrationTest {

    private static final MariaDBContainer<?> MARIADB = SharedContainersConfig.mariadb;
    private static final String CODE_PATTERN = "^[a-z0-9]{6}$";

    private String database;
    private JdbcTemplate jdbc;
    private Flyway flywayToV53;

    @BeforeEach
    void setUp() {
        MARIADB.start();
        database = "v53_code_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        rootJdbc().execute("CREATE DATABASE " + database);

        DriverManagerDataSource dataSource = dataSource(database);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("52").load().migrate();
        flywayToV53 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("53").load();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        rootJdbc().execute("DROP DATABASE IF EXISTS " + database);
    }

    @Test
    void 모든_작품과_공유_포트폴리오만_작가_안에서_유일한_6자_코드를_받는다() {
        // 작가 여러 명 — 한 작가는 작품을 많이 두어 같은 작가 안 유일성 검사의 표본을 늘린다.
        String prolific = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        String third = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO artworks (id, author_id, status, created_at, updated_at)
                SELECT CONCAT('aw-', LPAD(seq, 33, '0')), ?, 'READY', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM seq_1_to_300
                """, prolific);
        insertArtwork(other, "READY");
        insertArtwork(other, "PROCESSING");
        String trashed = insertArtwork(third, "DELETED");   // 휴지통 작품도 행이 남으므로 코드를 받는다
        String failed = insertArtwork(third, "FAILED");

        String artistPage = insertPortfolio(other, "ARTIST_PAGE", "LIVE", null);
        String sharedLive = insertPortfolio(other, "SHARED", "LIVE", "legacy-slug-live-000001");
        String sharedSnapshot = insertPortfolio(other, "SHARED", "SNAPSHOT", "legacy-slug-snap-000002");
        String thirdArtistPage = insertPortfolio(third, "ARTIST_PAGE", "LIVE", null);
        String thirdShared = insertPortfolio(third, "SHARED", "LIVE", "legacy-slug-third-00003");

        flywayToV53.migrate();

        // 작품: 전부 형식에 맞는 코드, 작가 안 유일
        List<String> artworkCodes = jdbc.queryForList("SELECT public_code FROM artworks", String.class);
        assertThat(artworkCodes).hasSize(304).allSatisfy(code -> assertThat(code).matches(CODE_PATTERN));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT author_id, public_code FROM artworks) t", Integer.class))
                .isEqualTo(304);
        assertThat(codeOfArtwork(trashed)).matches(CODE_PATTERN);
        assertThat(codeOfArtwork(failed)).matches(CODE_PATTERN);

        // 포트폴리오: SHARED만 코드, ARTIST_PAGE는 NULL, 옛 slug는 그대로
        for (String id : List.of(sharedLive, sharedSnapshot, thirdShared)) {
            assertThat(codeOfPortfolio(id)).as(id).matches(CODE_PATTERN);
        }
        assertThat(codeOfPortfolio(artistPage)).isNull();
        assertThat(codeOfPortfolio(thirdArtistPage)).isNull();
        assertThat(jdbc.queryForObject("SELECT share_slug FROM portfolios WHERE id = ?", String.class, sharedLive))
                .isEqualTo("legacy-slug-live-000001");
        assertThat(codeOfPortfolio(sharedLive)).isNotEqualTo(codeOfPortfolio(sharedSnapshot));

        // 제약: 작품 NOT NULL, 포트폴리오 NULL 허용
        assertThat(isNullable("artworks")).isEqualTo("NO");
        assertThat(isNullable("portfolios")).isEqualTo("YES");
    }

    @Test
    void 적용_후_같은_작가의_같은_코드는_UNIQUE로_막히고_다른_작가는_허용된다() {
        String author = UUID.randomUUID().toString();
        String owner = UUID.randomUUID().toString();
        String artworkId = insertArtwork(author, "READY");
        String sharedId = insertPortfolio(owner, "SHARED", "LIVE", null);

        flywayToV53.migrate();

        String artworkCode = codeOfArtwork(artworkId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO artworks (id, author_id, public_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'READY', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), author, artworkCode))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("""
                INSERT INTO artworks (id, author_id, public_code, status, created_at, updated_at)
                VALUES (?, ?, ?, 'READY', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), UUID.randomUUID().toString(), artworkCode);

        String portfolioCode = codeOfPortfolio(sharedId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO portfolios (id, owner_member_id, kind, reflection_type, title, public_code, item_count,
                                        created_at, updated_at, last_edited_at)
                VALUES (?, ?, 'SHARED', 'LIVE', 't', ?, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), owner, portfolioCode))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 작가 페이지는 코드가 NULL이라 (owner, public_code) UNIQUE와 공존한다
        insertPortfolio(owner, "ARTIST_PAGE", "LIVE", null);

        // slug 없는 SHARED 여러 개도 uk_pf_slug(NULL 허용)에 걸리지 않는다 — 신규 slug 발급 중단 전제
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM portfolios WHERE owner_member_id = ? AND share_slug IS NULL", Integer.class,
                owner)).isEqualTo(2);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private String insertArtwork(String authorId, String status) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO artworks (id, author_id, status, deleted_at, created_at, updated_at)
                VALUES (?, ?, ?, IF(? = 'DELETED', UTC_TIMESTAMP(6), NULL), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, authorId, status, status);
        return id;
    }

    private String insertPortfolio(String ownerId, String kind, String reflectionType, String shareSlug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO portfolios (id, owner_member_id, kind, reflection_type, title, share_slug,
                                        artist_page_key, item_count, created_at, updated_at, last_edited_at)
                VALUES (?, ?, ?, ?, 't', ?, IF(? = 'ARTIST_PAGE', 'Y', NULL), 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, ownerId, kind, reflectionType, shareSlug, kind);
        return id;
    }

    private String codeOfArtwork(String id) {
        return jdbc.queryForObject("SELECT public_code FROM artworks WHERE id = ?", String.class, id);
    }

    private String codeOfPortfolio(String id) {
        return jdbc.queryForObject("SELECT public_code FROM portfolios WHERE id = ?", String.class, id);
    }

    private String isNullable(String table) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT IS_NULLABLE FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'public_code'
                """, table);
        return (String) row.get("IS_NULLABLE");
    }

    private static DriverManagerDataSource dataSource(String db) {
        String url = "jdbc:mariadb://" + MARIADB.getHost() + ":" + MARIADB.getMappedPort(3306)
                + (db == null ? "/" : "/" + db);
        return new DriverManagerDataSource(url, "root", MARIADB.getPassword());
    }

    private static JdbcTemplate rootJdbc() {
        return new JdbcTemplate(dataSource(null));
    }
}
