package com.atcrew.analytics.internal.application;

import java.time.Instant;

/**
 * PostHog {@code $insert_id} 생성(plans/261005-posthog-analytics-events).
 *
 * <p>성공 이벤트는 Modulith 이벤트 레지스트리를 거치므로 앱 재기동 시 미완료 건이 다시 발행된다.
 * 같은 사건이 두 번 전송돼도 PostHog가 한 건으로 합치도록, 사건 하나를 가리키는 고정 문자열을 만든다.
 * PostHog는 {@code $insert_id}가 아니라 이벤트 uuid로 중복을 판정하므로, 발행 구현체가 이 값과 발생 시각으로
 * uuid를 만든다(plans/261019-posthog-server-delivery).
 */
public final class AnalyticsInsertId {

    private AnalyticsInsertId() {
    }

    /** 대상 식별자만으로 사건이 특정되는 이벤트(작품 발행·북마크 등). */
    public static String of(String eventName, String targetId) {
        return eventName + ":" + targetId;
    }

    /** 같은 대상에 반복될 수 있는 이벤트(수정·공개범위 변경 등) — 발생 시각까지 넣어 구분한다. */
    public static String of(String eventName, String targetId, Instant occurredAt) {
        return of(eventName, targetId) + ":" + occurredAt.toEpochMilli();
    }
}
