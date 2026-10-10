package com.atcrew.billing;

import java.time.Instant;

/**
 * 취소 이력이 있는 회원이 다시 구독함(PA-08). Stripe는 재구독을 새 구독 객체로 만들기 때문에
 * 신규 생성 웹훅으로 들어오며, 같은 회원의 취소된 구독이 남아 있는지로 최초 활성화와 구분한다.
 *
 * @param memberId       구독 회원
 * @param subscriptionId 새로 만들어진 Stripe 구독 ID
 * @param plan           재구독한 프로 플랜. 결제 주기는 이 값에서 유도한다
 * @param daysSinceEnd   직전 구독의 주기 종료 시각으로부터 지난 일수. 종료 시각을 모르면 null
 * @param reactivatedAt  Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionReactivatedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        Long daysSinceEnd,
        Instant reactivatedAt,
        String stripeEventId
) {
}
