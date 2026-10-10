package com.atcrew.billing;

/**
 * 구독이 끝난 사유. 삭제 웹훅이 도착하기 직전까지 미러에 남아 있던 상태로 판정한다 —
 * Stripe가 보내는 {@code cancellation_details}는 비어 오는 경우가 있어 근거로 쓰지 않는다.
 */
public enum SubscriptionEndReason {

    VOLUNTARY, // 회원이 주기 종료 취소를 예약해 둔 뒤 그 주기가 끝남
    PAYMENT_FAILED, // 결제 실패로 복구 대기(PAST_DUE)였다가 끝남
    OTHER // 그 외 — 즉시 취소, 운영 조치, 미러에 없던 구독의 삭제
}
