package com.atcrew.billing;

import java.time.Instant;

/**
 * 예약했던 취소를 되돌림(PA-08). {@code cancel_at_period_end}가 꺼지는 것으로 확인한다.
 *
 * @param memberId         구독 회원
 * @param subscriptionId   Stripe 구독 ID
 * @param plan             구독을 이어가는 프로 플랜
 * @param currentPeriodEnd 이어지는 현재 주기 종료 시각(UTC). 알 수 없으면 null
 * @param revokedAt        Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId    Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionCancelRequestRevokedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        Instant currentPeriodEnd,
        Instant revokedAt,
        String stripeEventId
) {
}
