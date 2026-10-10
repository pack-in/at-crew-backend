package com.atcrew.analytics.internal.application;

import java.time.Instant;

/**
 * PostHog {@code $insert_id} 생성(plans/261005-posthog-analytics-events).
 *
 * <p>성공 이벤트는 Modulith 이벤트 레지스트리를 거치므로 앱 재배포 시 미완료 건이 다시 발행된다.
 * 같은 사건이 두 번 전송돼도 PostHog가 중복으로 세지 않도록, 사건 하나를 가리키는 고정 문자열을 만든다.
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
