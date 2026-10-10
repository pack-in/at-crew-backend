package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독이 처음 활성화됨(PA-08). 과거 취소 이력이 있는 회원의 재구독은
 * {@link SubscriptionReactivatedEvent}로 나가며, 두 이벤트는 동시에 발행되지 않는다.
 *
 * @param memberId       구독 회원
 * @param subscriptionId Stripe 구독 ID
 * @param plan           활성화된 프로 플랜. 결제 주기는 이 값에서 유도한다
 * @param activatedAt    Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionActivatedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        Instant activatedAt,
        String stripeEventId
) {
}
