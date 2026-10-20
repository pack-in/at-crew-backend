package com.atcrew.analytics;

import java.time.Instant;
import java.util.Map;

/**
 * 분석 모듈의 공개 포트. 다른 모듈은 이 인터페이스로만 분석 이벤트를 발행한다
 * (plans/261005-posthog-analytics-events).
 *
 * <p>구현체는 PostHog에 동기로 보내고, 전송에 실패하면 예외를 던진다(plans/261019-posthog-server-delivery).
 * 호출자는 analytics 리스너뿐이다 — 성공 리스너는 예외로 끝나 Modulith 발행 기록이 미완료로 남고 재기동 때
 * 다시 발행된다. 비즈니스 트랜잭션은 이미 커밋·롤백이 끝난 뒤라 예외가 비즈니스 흐름을 되돌리지 않는다.
 *
 * <p>공통 속성(account_type/app_env/schema_version/plan_type/locale)은 구현체가 조립해 붙이므로 호출자는
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
     * @param insertId   사건을 특정하는 고정 문자열({@code AnalyticsInsertId}). 이 값과 {@code occurredAt}으로
     *                   이벤트 UUID를 만들어, 같은 사건이 재발행되면 PostHog가 한 건으로 합치게 한다.
     *                   null이면 매번 새 UUID를 쓴다
     * @param occurredAt 사건 발생 시각. null이면 전송 시각을 쓰므로 재발행된 이벤트가 한 건으로 합쳐지지 않는다
     */
    void publish(String eventName, String memberId, Map<String, Object> properties, String insertId,
                 Instant occurredAt);

    /**
     * 회원이 특정되지 않은 이벤트를 발행한다(가입 실패처럼 아직 회원이 없는 흐름). person profile을 만들지 않는
     * personless 이벤트로 보내고, 공통 속성에서 plan_type·locale은 빠진다.
     *
     * @param eventName  PostHog 이벤트명
     * @param distinctId 호출자가 정하는 임시 distinct_id
     * @param properties 이벤트 고유 속성. null이거나 값이 null인 항목은 전송하지 않는다
     * @param insertId   {@link #publish}와 같다
     * @param occurredAt 사건 발생 시각. null이면 전송 시각을 쓴다
     */
    void publishAnonymous(String eventName, String distinctId, Map<String, Object> properties, String insertId,
                          Instant occurredAt);
}
