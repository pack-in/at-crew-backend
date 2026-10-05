package com.atcrew.billing;

/**
 * 구독 결제가 최초 결제인지 정기 갱신인지(PA-09). {@code billing_reason}이 아니라 구독에 실결제
 * ({@code Subscription.firstPaymentAt})가 한 번도 없었는지로 판정한다(code-review 2026-10-06) —
 * 무료 체험 종료 후 첫 청구는 Stripe가 billing_reason=subscription_cycle로 보내지만, 이 구독의 첫
 * 실결제라는 사실은 바뀌지 않으므로 INITIAL로 집계한다.
 */
public enum SubscriptionPaymentType {

    INITIAL, // 이 구독의 첫 실결제
    RENEWAL // 이미 한 번 이상 실결제가 있었던 구독의 그 다음 정기 결제
}
