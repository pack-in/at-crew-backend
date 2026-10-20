package com.atcrew.analytics.internal.application;

import com.atcrew.analytics.internal.config.PostHogCaptureClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * PostHog 전송 페이로드·실패 처리 단위 테스트(plans/261019-posthog-server-delivery).
 *
 * <p>PostHog는 uuid·이벤트명·timestamp·distinct_id가 모두 같으면 한 건으로 합친다. 재기동 재발행이 중복으로
 * 세지지 않으려면 같은 사건은 같은 uuid·timestamp로 나가야 하고, 다른 사건은 달라야 한다.
 */
class PostHogAnalyticsEventPublisherTest {

    private static final String HOST = "https://posthog.test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CommonPropertiesSupplier commonProperties = mock(CommonPropertiesSupplier.class);
    private final List<JsonNode> sentEvents = new ArrayList<>();

    private MockRestServiceServer server;
    private PostHogAnalyticsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(HOST);
        server = MockRestServiceServer.bindTo(builder).build();
        publisher = new PostHogAnalyticsEventPublisher(new PostHogCaptureClient(builder.build(), "phc_test"),
                commonProperties);
        when(commonProperties.of("member-1")).thenAnswer(invocation -> new LinkedHashMap<>(Map.of(
                "account_type", "creator", "app_env", "production", "plan_type", "PRO_MONTHLY")));
        when(commonProperties.ofEnvironment()).thenAnswer(invocation -> new LinkedHashMap<>(Map.of(
                "account_type", "unknown", "app_env", "production")));
    }

    @Test
    void 회원_이벤트는_공통속성과_고유속성을_합쳐_사건_시각으로_보낸다() {
        expectSuccess();
        Map<String, Object> properties = new HashMap<>();
        properties.put("content_id", "artwork-1");
        properties.put("folder_id", null);

        publisher.publish("bookmark_saved", "member-1", properties, "bookmark_saved:member-1:artwork-1:1",
                Instant.parse("2026-10-05T01:02:03.456789Z"));

        JsonNode event = singleSentEvent();
        assertThat(event.get("event").asString()).isEqualTo("bookmark_saved");
        assertThat(event.get("distinct_id").asString()).isEqualTo("member-1");
        // 재발행 때 직렬화를 거쳐도 같은 값이 되도록 밀리초로 자른다.
        assertThat(event.get("timestamp").asString()).isEqualTo("2026-10-05T01:02:03.456Z");
        assertThat(event.get("uuid").asString()).isNotBlank();
        JsonNode sentProperties = event.get("properties");
        assertThat(sentProperties.get("account_type").asString()).isEqualTo("creator");
        assertThat(sentProperties.get("plan_type").asString()).isEqualTo("PRO_MONTHLY");
        assertThat(sentProperties.get("content_id").asString()).isEqualTo("artwork-1");
        assertThat(sentProperties.get("$insert_id").asString()).isEqualTo("bookmark_saved:member-1:artwork-1:1");
        assertThat(sentProperties.has("folder_id")).isFalse();
        assertThat(sentProperties.has("$process_person_profile")).isFalse();
    }

    @Test
    void 같은_사건을_다시_보내면_uuid와_timestamp가_같다() {
        expectSuccess();
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        publisher.publish("account_deleted", "member-1", Map.of(), "account_deleted:member-1", occurredAt);
        publisher.publish("account_deleted", "member-1", Map.of(), "account_deleted:member-1", occurredAt);

        assertThat(sentEvents).hasSize(2);
        assertThat(sentEvents.get(1).get("uuid")).isEqualTo(sentEvents.get(0).get("uuid"));
        assertThat(sentEvents.get(1).get("timestamp")).isEqualTo(sentEvents.get(0).get("timestamp"));
    }

    // insertId가 같아도 시각이 다르면 다른 사건이다(포트폴리오에 같은 작품을 뺐다가 다시 넣는 경우).
    @Test
    void insertId가_같아도_시각이_다르면_uuid가_다르다() {
        expectSuccess();

        publisher.publish("portfolio_artwork_added", "member-1", Map.of(), "portfolio_artwork_added:p-1:a-1",
                Instant.parse("2026-10-05T00:00:00Z"));
        publisher.publish("portfolio_artwork_added", "member-1", Map.of(), "portfolio_artwork_added:p-1:a-1",
                Instant.parse("2026-10-05T00:00:01Z"));

        assertThat(sentEvents.get(1).get("uuid")).isNotEqualTo(sentEvents.get(0).get("uuid"));
    }

    @Test
    void 발생_시각이_없으면_전송_시각을_쓴다() {
        expectSuccess();
        Instant before = Instant.now().minusMillis(1);

        publisher.publish("subscription_refunded", "member-1", Map.of(), "subscription_refunded:evt_1", null);

        Instant sentAt = Instant.parse(singleSentEvent().get("timestamp").asString());
        assertThat(sentAt).isAfter(before).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void 익명_이벤트는_person을_만들지_않도록_보낸다() {
        expectSuccess();

        publisher.publishAnonymous("auth_signup_failed", "anon-1", Map.of("error_code", "DUPLICATE_EMAIL"),
                "auth_signup_failed:anon-1", null);

        JsonNode sentProperties = singleSentEvent().get("properties");
        assertThat(sentProperties.get("$process_person_profile").asBoolean()).isFalse();
        assertThat(sentProperties.get("account_type").asString()).isEqualTo("unknown");
        assertThat(sentProperties.has("plan_type")).isFalse();
    }

    // 예외로 끝나야 성공 리스너의 발행 기록이 미완료로 남아 재기동 때 다시 발행된다.
    @Test
    void PostHog가_오류를_응답하면_예외를_던진다() {
        server.expect(requestTo(HOST + "/batch/")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> publisher.publish("account_deleted", "member-1", Map.of(),
                "account_deleted:member-1", null))
                .isInstanceOf(AnalyticsDeliveryException.class)
                .hasMessageContaining("account_deleted");
    }

    @Test
    void 연결에_실패하면_예외를_던진다() {
        server.expect(requestTo(HOST + "/batch/")).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> publisher.publishAnonymous("auth_signup_failed", "anon-1", Map.of(),
                "auth_signup_failed:anon-1", null))
                .isInstanceOf(AnalyticsDeliveryException.class);
    }

    private void expectSuccess() {
        server.expect(manyTimes(), requestTo(HOST + "/batch/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.get("api_key").asString()).isEqualTo("phc_test");
                    assertThat(body.get("batch")).hasSize(1);
                    sentEvents.add(body.get("batch").get(0));
                })
                .andRespond(withSuccess("{\"status\":1}", MediaType.APPLICATION_JSON));
    }

    private JsonNode singleSentEvent() {
        assertThat(sentEvents).hasSize(1);
        return sentEvents.getFirst();
    }
}
