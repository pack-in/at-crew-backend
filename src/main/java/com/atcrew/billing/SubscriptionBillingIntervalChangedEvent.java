package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독 결제 주기가 바뀜(PA-08). 월간↔연간 변경은 Checkout이 아니라 Customer Portal에서만
 * 일어나므로 구독 갱신 웹훅의 플랜 변화로만 확인할 수 있다.
 *
 * @param memberId       구독 회원
 * @param subscriptionId Stripe 구독 ID
 * @param previousPlan   변경 전 프로 플랜
 * @param newPlan        변경 후 프로 플랜
 * @param effectiveAt    Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionBillingIntervalChangedEvent(
        String memberId,
        String subscriptionId,
        PlanType previousPlan,
        PlanType newPlan,
        Instant effectiveAt,
        String stripeEventId
) {
}
