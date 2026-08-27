package com.atcrew.common.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 미완료 도메인 이벤트 수를 지표로 노출한다(docs/design/observability-design.md §6).
 *
 * <p>이벤트 소비가 막히면 검색 색인 갱신·이미지 후처리 같은 후속 작업이 조용히 멈춘다. API는 200을
 * 돌려주고 있어 HTTP 지표로는 드러나지 않으므로, 레지스트리에 쌓이는 미완료 행 수를 직접 본다.
 *
 * <p>스크레이프마다 DB를 조회하지 않고 주기적으로 갱신한 값을 gauge가 읽는다 — 스크레이프 간격이
 * 짧아지거나 수집기가 늘어도 DB 부하가 그만큼 늘지 않게 한다.
 */
@Component
class EventPublicationMetrics {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationMetrics.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong incompleteCount = new AtomicLong();

    EventPublicationMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("atcrew.modulith.incomplete.events", incompleteCount, AtomicLong::doubleValue)
                .description("완료되지 않은 도메인 이벤트 수 — 이벤트 소비 정체를 나타낸다")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    void refresh() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL", Long.class);
            incompleteCount.set(count == null ? 0L : count);
        } catch (Exception e) {
            // 지표 수집 실패가 애플리케이션에 영향을 주면 안 된다. 값은 직전 것을 유지한다.
            log.warn("미완료 이벤트 수 조회 실패", e);
        }
    }
}
