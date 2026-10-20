package com.atcrew.analytics.internal.application;

/**
 * PostHog 전송 실패(plans/261019-posthog-server-delivery). 성공 리스너가 이 예외로 끝나면 Modulith 발행
 * 기록이 미완료로 남아 재기동 때 다시 발행된다.
 */
class AnalyticsDeliveryException extends RuntimeException {

    AnalyticsDeliveryException(String eventName, String insertId, Throwable cause) {
        super("PostHog 이벤트 전송 실패: event=" + eventName + ", insertId=" + insertId, cause);
    }
}
