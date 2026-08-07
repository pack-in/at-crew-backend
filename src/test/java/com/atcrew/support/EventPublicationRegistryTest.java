package com.atcrew.support;

import com.atcrew.AtCrewBackendApplication;
import com.atcrew.SharedContainersConfig;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith 이벤트 레지스트리의 JDBC(MariaDB) 전환 검증
 * (docs/design/mariadb-migration-design.md §3.8 — P5 게이트).
 *
 * <p>Mongo 시절 이벤트 발행 ID(UUID) 인코딩 문제로 {@code CodecConfigurationException}이 났던 전례가 있어
 * 동일 계열 결함이 JDBC 레지스트리에서 재발하지 않는지를 세 가지로 확인한다:
 * <ol>
 *   <li>이벤트 발행 시 {@code EVENT_PUBLICATION} 테이블에 실제로 행이 INSERT되는가</li>
 *   <li>스키마가 Modulith 공식 SQL 그대로인가 — ID가 MariaDB 10.7+ 네이티브 UUID 타입이 아니라 VARCHAR(36)인가</li>
 *   <li>재기동 시 {@code republish-outstanding-events-on-restart}가 미완료 이벤트를 재발행하는가
 *       (= 저장된 UUID 문자열을 다시 읽어 리스너를 찾아 호출하고 완료 마킹까지 하는 왕복이 성립하는가)</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
@Import(EventPublicationRegistryTest.ProbeConfig.class)
// 재기동 검증(3번)은 1번이 남긴 미완료 행을 입력으로 쓰므로 실행 순서를 고정한다.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventPublicationRegistryTest {

    /** 이 테스트가 발행한 행을 다른 테스트가 남긴 행과 구분하는 식별자 */
    private static final String PROBE_PAYLOAD = "probe-" + UUID.randomUUID();

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Test
    @Order(1)
    void 이벤트를_발행하면_EVENT_PUBLICATION에_미완료_행이_INSERT된다() {
        // 리스너가 @TransactionalEventListener라 커밋 이후에만 동작한다 — 트랜잭션 경계를 직접 연다.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(new ProbeEvent(PROBE_PAYLOAD)));

        await(() -> ProbeListener.invocations.get() > 0);

        Map<String, Object> row = findProbeRow();
        // 저장된 ID가 UUID ↔ 문자열로 손실 없이 왕복해야 한다 (Mongo 시절 인코딩 사고 지점)
        String id = (String) row.get("ID");
        assertThat(UUID.fromString(id)).hasToString(id);
        assertThat(row.get("LISTENER_ID")).isEqualTo(ProbeListener.LISTENER_ID);
        assertThat(row.get("EVENT_TYPE")).isEqualTo(ProbeEvent.class.getName());
        // 리스너가 실패했으므로 완료 마킹이 되지 않은 채 남아야 한다 — 3번 검증의 입력이다.
        assertThat(row.get("COMPLETION_DATE")).isNull();
    }

    @Test
    @Order(2)
    void EVENT_PUBLICATION_스키마는_Modulith_공식_MariaDB_스키마_그대로다() {
        Map<String, Object> id = column("ID");
        // MariaDB 10.7+ 네이티브 UUID 타입을 쓰지 않는다 — 공식 스키마의 VARCHAR(36) 그대로여야 한다.
        assertThat((String) id.get("DATA_TYPE")).isEqualToIgnoringCase("varchar");
        assertThat(((Number) id.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(36);

        // v2 스키마(use-legacy-structure 기본값 false)가 기대하는 컬럼이 Flyway 마이그레이션에 포함돼 있어야 한다.
        assertThat((String) column("STATUS").get("DATA_TYPE")).isEqualToIgnoringCase("varchar");
        assertThat((String) column("COMPLETION_ATTEMPTS").get("DATA_TYPE")).isEqualToIgnoringCase("int");
        assertThat((String) column("LAST_RESUBMISSION_DATE").get("DATA_TYPE")).isEqualToIgnoringCase("timestamp");
    }

    @Test
    @Order(3)
    void 재기동하면_미완료_이벤트가_재발행되어_완료_마킹된다() {
        // 다른 테스트가 남긴 미완료 행까지 재발행되면 이 테스트의 검증 대상이 흐려지므로 프로브 행만 남긴다.
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION WHERE EVENT_TYPE <> ?", ProbeEvent.class.getName());
        assertThat(findProbeRow().get("COMPLETION_DATE")).isNull();

        int before = ProbeListener.invocations.get();
        ProbeListener.shouldFail = false;

        // 같은 MariaDB를 가리키는 새 ApplicationContext = 재기동. republish 옵션을 켜고 띄운다.
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(
                AtCrewBackendApplication.class, ProbeConfig.class)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "server.port=0",
                        "spring.modulith.events.republish-outstanding-events-on-restart=true",
                        "spring.datasource.url=" + SharedContainersConfig.mariadb.getJdbcUrl(),
                        "spring.datasource.username=" + SharedContainersConfig.mariadb.getUsername(),
                        "spring.datasource.password=" + SharedContainersConfig.mariadb.getPassword(),
                        "spring.elasticsearch.uris=http://" + SharedContainersConfig.elasticsearch.getHttpHostAddress())
                .run()) {

            await(() -> findProbeRow().get("COMPLETION_DATE") != null);
        }

        assertThat(ProbeListener.invocations.get()).isGreaterThan(before);
    }

    private Map<String, Object> findProbeRow() {
        return jdbcTemplate.queryForMap("""
                SELECT ID, LISTENER_ID, EVENT_TYPE, COMPLETION_DATE
                FROM EVENT_PUBLICATION
                WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?
                """, ProbeEvent.class.getName(), "%" + PROBE_PAYLOAD + "%");
    }

    private Map<String, Object> column(String columnName) {
        return jdbcTemplate.queryForMap("""
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'EVENT_PUBLICATION' AND COLUMN_NAME = ?
                """, columnName);
    }

    /** 리스너가 비동기(@Async)라 반영까지 폴링한다. */
    private void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("이벤트 레지스트리 상태 반영 대기 시간 초과");
    }

    /** 레지스트리 검증 전용 이벤트 — 실제 도메인 이벤트를 쓰면 다른 모듈 리스너까지 끌려 들어온다. */
    record ProbeEvent(
            String payload  // 이 테스트가 발행한 행을 식별하는 값
    ) {
    }

    /**
     * 첫 발행에서는 일부러 실패해 미완료 행을 남기고, 재기동 후 재발행에서는 성공하는 프로브 리스너.
     *
     * <p>{@code @ApplicationModuleListener}(= @Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener)를
     * 직접 펼쳐 쓰는 이유는 리스너 ID를 명시 고정하기 위해서다 — 재기동 후 컨텍스트에서도 같은 ID로 매칭돼야
     * 미완료 행이 이 리스너로 재발행된다.
     */
    static class ProbeListener {

        static final String LISTENER_ID = "event-publication-registry-probe";

        static final AtomicInteger invocations = new AtomicInteger();
        static volatile boolean shouldFail = true;

        @Async
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @TransactionalEventListener(id = LISTENER_ID)
        void on(ProbeEvent event) {
            invocations.incrementAndGet();
            if (shouldFail) {
                throw new ProbeFailure();
            }
        }
    }

    /** 첫 발행을 미완료 상태로 남기기 위한 의도된 실패 신호. */
    static class ProbeFailure extends RuntimeException {
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        ProbeListener eventPublicationRegistryProbeListener() {
            return new ProbeListener();
        }
    }
}
