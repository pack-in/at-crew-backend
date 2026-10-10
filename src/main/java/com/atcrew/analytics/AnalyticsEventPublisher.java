package com.atcrew.analytics;

import java.util.Map;

/**
 * 분석 모듈의 공개 포트. 다른 모듈은 이 인터페이스로만 분석 이벤트를 발행한다
 * (plans/261005-posthog-analytics-events).
 *
 * <p>구현체는 전송 실패를 호출자에게 전파하지 않는다 — 분석 전송이 비즈니스 흐름을 막아서는 안 된다.
 * 공통 속성(app_env/schema_version/plan_type/locale)은 구현체가 조립해 붙이므로 호출자는
 * 이벤트 고유 속성만 넘긴다.
 */
public interface AnalyticsEventPublisher {

    /**
     * 회원이 특정된 이벤트를 발행한다. distinct_id는 {@code memberId}이며, 공통 속성에
     * 그 회원의 plan_type·locale이 함께 실린다.
     *
     * @param eventName  PostHog 이벤트명(예: {@code artwork_published})
     * @param memberId   이벤트 주체 회원 ID — distinct_id로 그대로 쓰인다
     * @param properties 이벤트 고유 속성. null이거나 값이 null인 항목은 전송하지 않는다
     * @param insertId   중복 집계 방지용 {@code $insert_id}. null이면 붙이지 않는다
     */
    void publish(String eventName, String memberId, Map<String, Object> properties, String insertId);

    /**
     * 회원이 특정되지 않은 이벤트를 발행한다(가입 실패처럼 아직 회원이 없는 흐름).
     * 공통 속성에서 plan_type·locale은 빠지고 app_env·schema_version만 실린다.
     *
     * @param eventName  PostHog 이벤트명
     * @param distinctId 호출자가 정하는 distinct_id — 회원 ID가 없을 때 쓸 식별자
     * @param properties 이벤트 고유 속성. null이거나 값이 null인 항목은 전송하지 않는다
     * @param insertId   중복 집계 방지용 {@code $insert_id}. null이면 붙이지 않는다
     */
    void publishAnonymous(String eventName, String distinctId, Map<String, Object> properties, String insertId);
}
