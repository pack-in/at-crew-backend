package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독이 완전히 끝남(PA-08). {@code customer.subscription.deleted} 웹훅에 대응하며,
 * 이 시점에 프로 혜택이 사라진다.
 *
 * @param memberId       구독이 끝난 회원
 * @param subscriptionId Stripe 구독 ID
 * @param plan           끝난 시점의 프로 플랜
 * @param endReason      종료 사유
 * @param endedAt        Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionEndedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        SubscriptionEndReason endReason,
        Instant endedAt,
        String stripeEventId
) {
}
