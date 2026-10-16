package com.atcrew.member.internal.persistence;

import com.atcrew.SharedContainersConfig;
import com.atcrew.member.internal.domain.HandleValidator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V52__member_handle_ledger.sql 마이그레이션 검증 (docs/design/member-handle-design.md §4.2, §5).
 *
 * <p>공유 MariaDB 컨테이너에 테스트마다 별도 데이터베이스를 만들고 V52 직전 버전까지 적용 → 픽스처 insert → V52 적용
 * 순으로 실제 운영 배포 순서를 재현한다. 새 handle 숫자는 RAND()라 정확한 값 대신 형식·불변식으로 검증한다.
 *
 * <p>V51은 병렬 브랜치(feat/og-image)가 쓰는 번호라 이 브랜치에는 아직 없다. 1단계 target을 {@code "51?"}로 두면
 * Flyway가 "target 버전이 없어도 실패하지 않음"(failOnMissingTarget=false)으로 동작해, V51이 없으면 V50까지, 병합 후
 * V51이 생기면 V51까지 적용한다 — 어느 쪽이든 2단계(target 52)는 V52 하나만 적용한다.
 */
class MemberHandleMigrationTest {

    private static final MariaDBContainer<?> MARIADB = SharedContainersConfig.mariadb;

    private String database;
    private JdbcTemplate jdbc;
    private Flyway flywayToV52;

    @BeforeEach
    void setUp() {
        MARIADB.start();
        database = "v52_handle_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        rootJdbc().execute("CREATE DATABASE " + database);

        DriverManagerDataSource dataSource = dataSource(database);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("51?").load().migrate();
        flywayToV52 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("52").load();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        rootJdbc().execute("DROP DATABASE IF EXISTS " + database);
    }

    @Test
    void 규칙_위반_handle은_일괄_변경되고_옛_handle과_탈퇴_handle은_원장에_남는다() throws Exception {
        // 규칙 위반 — 기대하는 새 handle 이름부(마지막 '_' 앞, 영문·숫자만, 최대 8자, 4자 미만이면 user)
        List<String> violating = new ArrayList<>(List.of(
                "portfolioqa_b27d614a",   // 20자 (운영 실데이터 형태) → portfoli
                "pa10starter_19c458a2",   // 20자 (운영 실데이터 형태) → pa10star
                "abcdefghijklmnopqrst",   // 20자, 밑줄 없음 → abcdefgh
                "has-hyphen_1a2b3c4d",    // 하이픈 → hashyphe
                "double__under",          // 연속 밑줄 → double
                "abc",                    // 3자 → user
                "ㄱ_1234abcd",            // 이름부에 영문 없음 → user
                "x-y_1234abcd"));         // 이름부 'x-y' → 'xy' 2자 → user
        List<String> reserved = reservedWords();
        for (int i = 0; i < reserved.size(); i++) {
            // 대소문자 무관 판정 확인을 위해 절반은 대문자로 넣는다
            violating.add(i % 2 == 0 ? reserved.get(i) : reserved.get(i).toUpperCase());
        }

        // 규칙 통과 — 그대로 유지돼야 한다. 새 handle 후보 형식과 겹치는 값도 섞는다.
        List<String> valid = List.of("user_1a2b3c4d", "Pack_1n", "_edge_", "portfoli_00000", "user_00000",
                "Searcher");

        Map<String, String> violatorIds = new LinkedHashMap<>();
        violating.forEach(h -> violatorIds.put(h, insertMember(h, true)));
        Map<String, String> validIds = new LinkedHashMap<>();
        valid.forEach(h -> validIds.put(h, insertMember(h, true)));

        // 탈퇴 회원 — handle은 비워져 있고 고정형 스냅샷 JSON에만 남아 있다.
        String withdrawn = insertMember(null, false);
        insertSnapshotPortfolio(withdrawn, "{\"name\": \"탈퇴작가\", \"handle\": \"gone_artist1\"}");
        insertSnapshotPortfolio(withdrawn, "{\"name\": \"탈퇴작가\", \"handle\": \"gone_artist1\"}"); // 중복 스냅샷
        // 탈퇴 회원 스냅샷 handle이 활성 회원 handle과 같으면(대소문자 무관) 활성 소유가 유지돼야 한다.
        String withdrawnClash = insertMember(null, false);
        insertSnapshotPortfolio(withdrawnClash, "{\"name\": \"충돌\", \"handle\": \"PACK_1N\"}");
        // handle이 JSON null이거나 키가 없는 스냅샷은 무시된다.
        String withdrawnNoHandle = insertMember(null, false);
        insertSnapshotPortfolio(withdrawnNoHandle, "{\"name\": \"무핸들\", \"handle\": null}");
        insertSnapshotPortfolio(withdrawnNoHandle, "{\"name\": \"무핸들\"}");
        // 활성 회원의 스냅샷은 복구 대상이 아니다.
        insertSnapshotPortfolio(validIds.get("Pack_1n"), "{\"name\": \"활성\", \"handle\": \"stale_handle\"}");

        flywayToV52.migrate();

        // 모든 활성 handle이 새 규칙을 통과한다 (SQL·Java 예약어 목록 어긋남도 여기서 잡힌다)
        List<String> activeHandles = jdbc.queryForList(
                "SELECT handle FROM members WHERE is_active = 1", String.class);
        assertThat(activeHandles).hasSize(violating.size() + valid.size());
        assertThat(activeHandles).allSatisfy(h -> assertThat(HandleValidator.check(h)).as(h).isEmpty());

        // 불변식: 활성 회원의 현재 handle은 원장에 그 회원 소유로 (대소문자까지 같게) 존재한다
        Integer missing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM members m
                WHERE m.is_active = 1
                  AND NOT EXISTS (SELECT 1 FROM member_handles h
                                  WHERE BINARY h.handle = BINARY m.handle AND h.member_id = m.id)
                """, Integer.class);
        assertThat(missing).isZero();

        // 규칙 위반 handle: 새 형식으로 바뀌고, 옛 handle은 원장에 남는다
        violatorIds.forEach((oldHandle, memberId) -> {
            String current = currentHandle(memberId);
            assertThat(current).as(oldHandle).matches(expectedStem(oldHandle) + "_[0-9]{5}");
            assertThat(ownerOf(oldHandle)).as(oldHandle).isEqualTo(memberId);
        });

        // 규칙 통과 handle은 그대로
        validIds.forEach((handle, memberId) -> assertThat(currentHandle(memberId)).isEqualTo(handle));

        // 탈퇴 회원 handle 복구, 활성 소유 우선, null·키 없음·활성 회원 스냅샷은 무시
        assertThat(ownerOf("gone_artist1")).isEqualTo(withdrawn);
        assertThat(ownerOf("pack_1n")).isEqualTo(validIds.get("Pack_1n"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_handles WHERE member_id IN (?, ?)",
                Integer.class, withdrawnClash, withdrawnNoHandle)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_handles WHERE handle IN ('null', 'stale_handle')",
                Integer.class)).isZero();

        // 원장 = 활성 현재 handle + 옛 handle(위반분) + 탈퇴 복구분
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_handles", Integer.class))
                .isEqualTo(valid.size() + violating.size() * 2 + 1);

        // 일괄 변경은 운영자 변경 취급 — 30일 제한 미산정
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE handle_changed_at IS NOT NULL",
                Integer.class)).isZero();

        // 작업용 테이블은 지워진다
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                        + " AND table_name = 'v52_handle_renames'", Integer.class)).isZero();
    }

    @Test
    void 재추첨_2회_후에도_충돌하면_마이그레이션이_실패한다() {
        String violator = insertMember("abc", true); // 새 handle은 user_ddddd 공간에서만 뽑힌다
        // user_00000 ~ user_99999 전부를 탈퇴 회원 스냅샷으로 원장에 선점시킨다 → 모든 추첨이 충돌
        String withdrawn = insertMember(null, false);
        jdbc.update("""
                INSERT INTO portfolios (id, owner_member_id, kind, reflection_type, title, item_count,
                                        snapshot_at, snapshot_owner_profile, created_at, updated_at, last_edited_at)
                SELECT LPAD(seq, 36, '0'), ?, 'SHARED', 'SNAPSHOT', 't', 0, UTC_TIMESTAMP(6),
                       JSON_OBJECT('name', 'n', 'handle', CONCAT('user_', LPAD(seq, 5, '0'))),
                       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                FROM seq_0_to_99999
                """, withdrawn);

        assertThatThrownBy(flywayToV52::migrate).hasStackTraceContaining("Duplicate entry");

        // 조용히 덮어쓰지 않는다 — 회원 handle은 바뀌지 않은 채 실패가 드러난다
        assertThat(currentHandle(violator)).isEqualTo("abc");
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private String insertMember(String handle, boolean active) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO members (id, handle, name, is_active, created_at, updated_at)
                VALUES (?, ?, '작가', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, handle, active);
        return id;
    }

    private void insertSnapshotPortfolio(String ownerId, String ownerProfileJson) {
        jdbc.update("""
                INSERT INTO portfolios (id, owner_member_id, kind, reflection_type, title, item_count,
                                        snapshot_at, snapshot_owner_profile, created_at, updated_at, last_edited_at)
                VALUES (?, ?, 'SHARED', 'SNAPSHOT', 't', 0, UTC_TIMESTAMP(6), ?,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), ownerId, ownerProfileJson);
    }

    private String currentHandle(String memberId) {
        return jdbc.queryForObject("SELECT handle FROM members WHERE id = ?", String.class, memberId);
    }

    private String ownerOf(String handle) {
        List<String> owners = jdbc.queryForList(
                "SELECT member_id FROM member_handles WHERE handle = ?", String.class, handle);
        return owners.isEmpty() ? null : owners.getFirst();
    }

    // 설계 §4.2 새 값 이름부: 마지막 '_' 앞부분(없으면 전체)에서 영문·숫자만 남겨 최대 8자, 4자 미만이면 user
    private static String expectedStem(String oldHandle) {
        int lastUnderscore = oldHandle.lastIndexOf('_');
        String head = lastUnderscore >= 0 ? oldHandle.substring(0, lastUnderscore) : oldHandle;
        String stem = head.replaceAll("[^A-Za-z0-9]", "");
        stem = stem.substring(0, Math.min(8, stem.length()));
        return stem.length() >= 4 ? stem : "user";
    }

    // SQL 마이그레이션의 예약어 목록이 HandleValidator와 어긋나면 첫 테스트가 실패하도록 Java 목록을 그대로 쓴다.
    @SuppressWarnings("unchecked")
    private static List<String> reservedWords() throws Exception {
        Field field = HandleValidator.class.getDeclaredField("RESERVED");
        field.setAccessible(true);
        return new ArrayList<>((Set<String>) field.get(null)).stream().sorted().toList();
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
