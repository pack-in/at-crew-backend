package com.atcrew.billing;

/**
 * Stripe 환불 완료(부분·전체 모두) — analytics가 PostHog {@code subscription_refunded}로 옮긴다.
 * 단건 상품 권한 회수(전체 환불만 대상)와는 별개로, 환불 자체는 부분이어도 기록한다.
 *
 * @param refundType    {@code "full"} 또는 {@code "partial"}
 * @param amount        환불된 금액(센트 단위, {@code Charge.getAmountRefunded()})
 * @param stripeEventId Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionRefundedEvent(String memberId, String refundType, long amount, String currency,
                                         String stripeEventId) {
}
