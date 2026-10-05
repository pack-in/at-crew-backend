package com.atcrew.member;

import java.time.Instant;

/**
 * 회원 탈퇴 처리 중 실패했을 때 발행되는 이벤트(plans/261005-posthog-analytics-events PA-04).
 *
 * <p>{@code deactivate()}가 예외로 끝나는 모든 경우(회원 없음·이미 탈퇴 등)에 대해 발행되며,
 * 원래 예외는 그대로 다시 던져진다 — 이 이벤트는 분석 전송용이고 호출자의 에러 처리 흐름에는
 * 영향을 주지 않는다.
 */
public record AccountDeletionFailedEvent(
        String memberId,    // 탈퇴를 시도한 회원 ID
        String errorCode,   // 실패 원인 에러 코드. DomainException이 아니면 "UNKNOWN"
        Instant occurredAt  // $insert_id에 섞어 같은 날 반복 실패가 한 건으로 뭉개지지 않게 한다
) {
}
