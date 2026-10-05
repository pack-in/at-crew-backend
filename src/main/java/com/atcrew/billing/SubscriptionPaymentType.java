package com.atcrew.billing;

/**
 * 구독 결제가 최초 결제인지 정기 갱신인지(PA-09). Stripe invoice의 {@code billing_reason}으로 판정한다 —
 * 무료 체험 종료 후 첫 청구는 Stripe가 갱신(subscription_cycle)으로 보내므로 갱신으로 집계된다.
 */
public enum SubscriptionPaymentType {

    INITIAL, // 구독을 처음 시작할 때의 결제(billing_reason=subscription_create)
    RENEWAL // 결제 주기가 돌아와 발생한 정기 갱신 결제(billing_reason=subscription_cycle)
}
