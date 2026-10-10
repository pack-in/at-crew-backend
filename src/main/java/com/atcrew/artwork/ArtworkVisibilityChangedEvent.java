package com.atcrew.artwork;

import java.time.Instant;

/**
 * 작품 피드 공개 여부 변경(plans/261005-posthog-analytics-events PA-05) — analytics 모듈이 구독해
 * {@code artwork_visibility_changed}로 전송한다.
 *
 * <p>호출자({@code updatePublication})가 실제로 값이 바뀐 경우에만 발행한다 — 같은 값으로 재선언해도
 * 이벤트가 나가지 않는다. 반복될 수 있는 사건이라 {@code occurredAt}까지 {@code $insert_id}에 넣어 구분한다.
 */
public record ArtworkVisibilityChangedEvent(
        String artworkId,              // 대상 작품 ID
        String authorId,                // 변경한 회원 ID — distinct_id로 쓰인다
        Visibility previousVisibility,  // 변경 전 공개 여부
        Visibility newVisibility,       // 변경 후 공개 여부
        Instant occurredAt              // 발생 시각 — $insert_id 구분용
) {
}
