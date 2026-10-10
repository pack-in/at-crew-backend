package com.atcrew.billing;

import java.time.Instant;

/**
 * 주기 종료 시 취소가 예약됨(PA-08). 취소는 Customer Portal에서 이뤄지고 웹훅의
 * {@code cancel_at_period_end}가 켜지는 것으로 확인한다 — 이 시점에는 아직 프로 혜택이 유지된다.
 *
 * @param memberId         구독 회원
 * @param subscriptionId   Stripe 구독 ID
 * @param plan             취소를 예약한 프로 플랜
 * @param currentPeriodEnd 혜택이 끝나는 현재 주기 종료 시각(UTC). 알 수 없으면 null
 * @param requestedAt      Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId    Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionCancelRequestedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        Instant currentPeriodEnd,
        Instant requestedAt,
        String stripeEventId
) {
}
