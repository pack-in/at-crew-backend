package com.atcrew.analytics.internal.application;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.BookmarkSavedEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 앱 컨텍스트에서 PostHog 전송 결과가 Modulith 발행 기록에 반영되는지 검증한다
 * (plans/261019-posthog-server-delivery).
 *
 * <p>예전 SDK는 전송 실패를 삼켜 리스너가 늘 성공으로 끝났고, Modulith가 기록을 완료 처리해 지웠다
 * ({@code completion-mode: delete}). 지금은 실패하면 기록이 남아 재기동 때 다시 발행된다.
 * PostHog 대신 로컬 HTTP 서버를 띄워, 본문에 {@link #FAIL_MARKER}가 든 요청만 500으로 응답한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
class AnalyticsDeliveryIntegrationTest {

    private static final String FAIL_MARKER = "posthog-should-fail";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Queue<String> RECEIVED_BODIES = new ConcurrentLinkedQueue<>();
    private static final HttpServer POSTHOG = startStubPostHog();

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void posthogHost(DynamicPropertyRegistry registry) {
        registry.add("posthog.host", () -> "http://127.0.0.1:" + POSTHOG.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        POSTHOG.stop(0);
    }

    @Test
    void 전송에_성공하면_발행_기록이_완료되고_본문이_PostHog_형식이다() {
        // 분석 리스너만 구독하는 이벤트라 다른 모듈의 부수 효과가 없다.
        String artworkId = "artwork-" + UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-05T01:02:03.456Z");

        publishBookmarkSaved(artworkId, occurredAt);

        await(() -> RECEIVED_BODIES.stream().anyMatch(body -> body.contains(artworkId)), "PostHog 요청이 오지 않았다");
        await(() -> publications(artworkId).isEmpty(), "완료된 발행 기록이 지워지지 않았다");

        JsonNode body = JSON.readTree(RECEIVED_BODIES.stream().filter(b -> b.contains(artworkId)).findFirst()
                .orElseThrow());
        JsonNode event = body.get("batch").get(0);
        assertThat(body.get("api_key").asString()).isEqualTo("test-posthog-api-key");
        assertThat(event.get("event").asString()).isEqualTo("bookmark_saved");
        assertThat(event.get("timestamp").asString()).isEqualTo("2026-10-05T01:02:03.456Z");
        assertThat(event.get("properties").get("content_id").asString()).isEqualTo(artworkId);
    }

    @Test
    void 전송에_실패하면_발행_기록이_미완료로_남는다() {
        String artworkId = "artwork-" + FAIL_MARKER + "-" + UUID.randomUUID();

        publishBookmarkSaved(artworkId, Instant.now());

        await(() -> publications(artworkId).stream().anyMatch(row -> "FAILED".equals(row.get("STATUS"))),
                "전송 실패한 발행 기록이 남지 않았다");
        Map<String, Object> publication = publications(artworkId).getFirst();
        assertThat(publication.get("COMPLETION_DATE")).isNull();
        assertThat((String) publication.get("LISTENER_ID")).contains("ArtworkAnalyticsListener");
    }

    private void publishBookmarkSaved(String artworkId, Instant occurredAt) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                new BookmarkSavedEvent("member-" + UUID.randomUUID(), artworkId, null, occurredAt)));
    }

    private List<Map<String, Object>> publications(String artworkId) {
        return jdbcTemplate.queryForList(
                "SELECT LISTENER_ID, STATUS, COMPLETION_DATE FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ?",
                "%" + artworkId + "%");
    }

    /** 리스너는 커밋 후 별도 스레드에서 돈다 — 조건이 맞을 때까지 기다린다. */
    private static void await(BooleanSupplier condition, String failureMessage) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(failureMessage);
    }

    private static HttpServer startStubPostHog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/batch/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED_BODIES.add(body);
                int status = body.contains(FAIL_MARKER) ? 500 : 200;
                byte[] response = "{\"status\":1}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
