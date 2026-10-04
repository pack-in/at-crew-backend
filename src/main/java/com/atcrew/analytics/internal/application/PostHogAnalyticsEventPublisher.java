package com.atcrew.analytics.internal.application;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.posthog.server.PostHogCaptureOptions;
import com.posthog.server.PostHogInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * PostHog 전송 구현(plans/261005-posthog-analytics-events).
 *
 * <p><b>예외를 삼킨다.</b> 분석 전송 실패가 작품 발행·결제 같은 비즈니스 흐름을 되돌리면 안 되므로
 * 호출자에게 전파하지 않고 경고 로그만 남긴다. 실패를 추적할 수 있도록 이벤트명과 {@code $insert_id}를
 * 함께 찍는다.
 *
 * <p><b>타임아웃.</b> SDK에 HTTP 타임아웃 설정 항목이 없다(2.3.2 기준 {@code PostHogConfig}에 proxy만 있다).
 * 대신 {@code capture()}는 메모리 큐에 넣고 바로 돌아오고 실제 전송은 SDK 내부 executor가 하므로
 * 호출 스레드가 네트워크를 기다리는 구간이 없다 — 느린 전송이 응답 지연으로 번지지 않는다.
 */
@Component
class PostHogAnalyticsEventPublisher implements AnalyticsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostHogAnalyticsEventPublisher.class);

    private static final String INSERT_ID = "$insert_id";

    private final PostHogInterface postHog;
    private final CommonPropertiesSupplier commonProperties;

    PostHogAnalyticsEventPublisher(PostHogInterface postHog, CommonPropertiesSupplier commonProperties) {
        this.postHog = postHog;
        this.commonProperties = commonProperties;
    }

    @Override
    public void publish(String eventName, String memberId, Map<String, Object> properties, String insertId) {
        capture(eventName, memberId, () -> commonProperties.of(memberId), properties, insertId);
    }

    @Override
    public void publishAnonymous(String eventName, String distinctId, Map<String, Object> properties, String insertId) {
        capture(eventName, distinctId, commonProperties::ofEnvironment, properties, insertId);
    }

    /** 공통 속성 조립도 try 안에서 한다 — 조립 단계의 실패 역시 호출자에게 전파하지 않는다. */
    private void capture(String eventName, String distinctId, Supplier<Map<String, Object>> common,
                         Map<String, Object> properties, String insertId) {
        try {
            PostHogCaptureOptions.Builder options = PostHogCaptureOptions.builder().properties(common.get());
            if (properties != null) {
                // SDK 속성 맵은 null 값을 허용하지 않는다(Kotlin Map<String, Any>) — 빈 값은 아예 보내지 않는다.
                properties.forEach((key, value) -> {
                    if (value != null) {
                        options.property(key, value);
                    }
                });
            }
            if (insertId != null && !insertId.isBlank()) {
                options.property(INSERT_ID, insertId);
            }
            postHog.capture(distinctId, eventName, options.build());
        } catch (RuntimeException e) {
            log.warn("PostHog 이벤트 전송 실패: event={}, insertId={}", eventName, insertId, e);
        }
    }
}
