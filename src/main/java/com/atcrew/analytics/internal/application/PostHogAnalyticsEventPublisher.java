package com.atcrew.analytics.internal.application;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.config.PostHogCaptureClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PostHog 전송 구현(plans/261005-posthog-analytics-events, 261019-posthog-server-delivery).
 *
 * <p><b>실패하면 예외를 던진다.</b> 로그만 남기고 삼키면 성공 리스너가 완료 처리돼 재발행 기회가 사라진다.
 * 던진 예외는 비동기 리스너의 예외 처리기가 ERROR로 남기고(→ Sentry), 미완료 발행 기록은
 * {@code atcrew_modulith_incomplete_events} 지표로 잡힌다.
 *
 * <p><b>중복 제거.</b> PostHog는 uuid·이벤트명·timestamp·distinct_id가 모두 같은 이벤트를 한 건으로 합친다
 * ({@code $insert_id}는 보지 않는다). 그래서 uuid를 insertId와 timestamp로 결정적으로 만들고, timestamp에는
 * 사건 발생 시각을 쓴다. 재발행된 이벤트는 직렬화를 거쳐도 값이 같도록 밀리초로 자른다.
 */
@Component
class PostHogAnalyticsEventPublisher implements AnalyticsEventPublisher {

    private static final String INSERT_ID = "$insert_id";
    private static final String PROCESS_PERSON_PROFILE = "$process_person_profile";

    private final PostHogCaptureClient client;
    private final CommonPropertiesSupplier commonProperties;

    PostHogAnalyticsEventPublisher(PostHogCaptureClient client, CommonPropertiesSupplier commonProperties) {
        this.client = client;
        this.commonProperties = commonProperties;
    }

    @Override
    public void publish(String eventName, String memberId, Map<String, Object> properties, String insertId,
                        Instant occurredAt) {
        send(eventName, memberId, commonProperties.of(memberId), properties, insertId, occurredAt);
    }

    @Override
    public void publishAnonymous(String eventName, String distinctId, Map<String, Object> properties,
                                 String insertId, Instant occurredAt) {
        Map<String, Object> common = commonProperties.ofEnvironment();
        // 임시 distinct_id마다 person이 생기면 사용자 수가 부풀어 오른다.
        common.put(PROCESS_PERSON_PROFILE, false);
        send(eventName, distinctId, common, properties, insertId, occurredAt);
    }

    private void send(String eventName, String distinctId, Map<String, Object> common,
                      Map<String, Object> properties, String insertId, Instant occurredAt) {
        Instant timestamp = (occurredAt == null ? Instant.now() : occurredAt).truncatedTo(ChronoUnit.MILLIS);
        boolean hasInsertId = insertId != null && !insertId.isBlank();

        Map<String, Object> merged = new LinkedHashMap<>(common);
        if (properties != null) {
            // 빈 값은 아예 보내지 않는다 — PostHog에서 null 속성과 속성 없음을 구분해 쓰지 않는다.
            properties.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        if (hasInsertId) {
            merged.put(INSERT_ID, insertId);
        }
        UUID uuid = hasInsertId
                ? UUID.nameUUIDFromBytes((insertId + "@" + timestamp.toEpochMilli()).getBytes(StandardCharsets.UTF_8))
                : UUID.randomUUID();

        try {
            client.send(eventName, distinctId, merged, timestamp, uuid);
        } catch (RestClientException e) {
            throw new AnalyticsDeliveryException(eventName, insertId, e);
        }
    }
}
