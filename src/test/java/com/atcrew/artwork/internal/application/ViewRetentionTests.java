package com.atcrew.artwork.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 익명 열람 기록 보관 배치(PA-06) 검증 — 홈-R14 "익명 식별자 기록은 1년 후 집계 통계만 보관".
 *
 * <p>배치는 작품 행을 보지 않으므로 작품 없이 이벤트·dedup 행만 직접 넣어 확인한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class ViewRetentionTests {

    // 기준 시각을 하루 중간으로 잡아, 같은 UTC 날짜가 경계 앞뒤로 갈리는 경우를 만든다.
    private static final Instant NOW = Instant.parse("2027-09-22T12:00:00Z");
    private static final Instant THRESHOLD = Instant.parse("2026-09-22T12:00:00Z");

    @Autowired
    ViewRetentionScheduler scheduler;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String artworkId = UUID.randomUUID().toString();

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM artwork_view_events");
        jdbcTemplate.update("DELETE FROM artwork_view_dedup");
        jdbcTemplate.update("DELETE FROM artwork_view_daily_stats");
    }

    @Test
    void 일년이_지난_익명_이벤트만_일별_통계로_합산하고_원본을_지운다() {
        insertEvent("ANONYMOUS", "FIRST", Instant.parse("2026-09-21T23:59:59Z"));
        insertEvent("ANONYMOUS", "FIRST", Instant.parse("2026-09-21T01:00:00Z"));
        insertEvent("ANONYMOUS", "REVISIT", Instant.parse("2026-09-21T02:00:00Z"));
        insertEvent("ANONYMOUS", "FIRST", THRESHOLD.minus(1, ChronoUnit.MICROS)); // 경계 직전 — 대상
        insertEvent("ANONYMOUS", "FIRST", THRESHOLD);                            // 경계 시각 — 보존
        insertEvent("ANONYMOUS", "FIRST", NOW.minusSeconds(60));
        insertEvent("MEMBER", "FIRST", Instant.parse("2025-01-01T00:00:00Z"));   // 회원 기록은 기한 없이 보존

        scheduler.purge(NOW);

        assertThat(dailyStats()).containsExactlyInAnyOrder(
                "2026-09-21|ANONYMOUS|FIRST|2",
                "2026-09-21|ANONYMOUS|REVISIT|1",
                "2026-09-22|ANONYMOUS|FIRST|1");
        assertThat(remainingEvents()).containsExactlyInAnyOrder(
                "ANONYMOUS|" + Timestamp.from(THRESHOLD),
                "ANONYMOUS|" + Timestamp.from(NOW.minusSeconds(60)),
                "MEMBER|" + Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")));
    }

    @Test
    void 다시_돌려도_이중_합산하지_않고_이후_만료분은_같은_날짜에_더한다() {
        insertEvent("ANONYMOUS", "FIRST", THRESHOLD.minusSeconds(3600));
        insertEvent("ANONYMOUS", "FIRST", THRESHOLD.plusSeconds(3600)); // 같은 UTC 날짜, 아직 1년 전이 아님

        scheduler.purge(NOW);
        scheduler.purge(NOW);
        assertThat(dailyStats()).containsExactly("2026-09-22|ANONYMOUS|FIRST|1");

        // 다음 실행 시점에는 나머지 이벤트도 1년이 지났다 — 같은 날짜 행에 더해진다.
        scheduler.purge(NOW.plusSeconds(7200));
        assertThat(dailyStats()).containsExactly("2026-09-22|ANONYMOUS|FIRST|2");
        assertThat(remainingEvents()).isEmpty();
    }

    @Test
    void 일년이_지난_익명_dedup_행만_지우고_회원_행은_남긴다() {
        String staleAnonymous = UUID.randomUUID().toString();
        String recentAnonymous = UUID.randomUUID().toString();
        String staleMember = UUID.randomUUID().toString();
        insertDedup("ANONYMOUS", staleAnonymous, THRESHOLD.minusSeconds(1));
        insertDedup("ANONYMOUS", recentAnonymous, THRESHOLD);
        insertDedup("MEMBER", staleMember, THRESHOLD.minusSeconds(86_400 * 30));

        scheduler.purge(NOW);

        assertThat(jdbcTemplate.queryForList("SELECT viewer_key FROM artwork_view_dedup", String.class))
                .containsExactlyInAnyOrder(recentAnonymous, staleMember);
    }

    private List<String> dailyStats() {
        return jdbcTemplate.query(
                "SELECT stat_date, viewer_type, view_kind, view_count FROM artwork_view_daily_stats WHERE artwork_id = ?",
                (rs, i) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getLong(4),
                artworkId);
    }

    private List<String> remainingEvents() {
        return jdbcTemplate.queryForList(
                        "SELECT viewer_type, viewed_at FROM artwork_view_events WHERE artwork_id = ?", artworkId)
                .stream()
                .map((Map<String, Object> row) -> row.get("viewer_type") + "|" + toTimestamp(row.get("viewed_at")))
                .toList();
    }

    private static Timestamp toTimestamp(Object value) {
        return value instanceof java.time.LocalDateTime ldt ? Timestamp.valueOf(ldt) : (Timestamp) value;
    }

    private void insertEvent(String viewerType, String viewKind, Instant viewedAt) {
        jdbcTemplate.update("""
                INSERT INTO artwork_view_events (id, artwork_id, viewer_type, viewer_key, view_kind, viewed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), artworkId, viewerType, UUID.randomUUID().toString(), viewKind,
                Timestamp.from(viewedAt));
    }

    private void insertDedup(String viewerType, String viewerKey, Instant lastCountedAt) {
        jdbcTemplate.update("""
                INSERT INTO artwork_view_dedup (artwork_id, viewer_type, viewer_key, last_counted_at)
                VALUES (?, ?, ?, ?)
                """, artworkId, viewerType, viewerKey, Timestamp.from(lastCountedAt));
    }
}
