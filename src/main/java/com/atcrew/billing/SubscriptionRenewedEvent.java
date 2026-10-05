package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독 정기 갱신 결제 성공(PA-09). 결제 실패 상태에서 되살아난 경우는 갱신이 아니라
 * {@link SubscriptionPaymentRecoveredEvent}로 나간다.
 *
 * @param memberId       결제한 회원
 * @param subscriptionId Stripe 구독 ID
 * @param plan           갱신된 프로 플랜. 결제 주기는 이 값에서 유도한다
 * @param renewalNumber  이번 결제가 몇 번째 갱신인지. 카운터 도입(V49) 이후 건만 셈
 * @param amount         실제 결제된 금액(USD 센트). invoice에 없으면 null
 * @param currency       통화 — USD 단일이지만 invoice가 준 값을 그대로 싣는다
 * @param renewedAt      Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionRenewedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        int renewalNumber,
        Long amount,
        String currency,
        Instant renewedAt,
        String stripeEventId
) {
}
