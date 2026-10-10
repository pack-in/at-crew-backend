package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독 최초 결제 성공(PA-09). 정기 갱신은 {@link SubscriptionRenewedEvent}, 결제 실패에서의 복구는
 * {@link SubscriptionPaymentRecoveredEvent}가 담당하며 세 이벤트는 같은 웹훅에서 동시에 나오지 않는다.
 *
 * @param memberId       결제한 회원
 * @param subscriptionId Stripe 구독 ID
 * @param plan           결제한 프로 플랜. 결제 주기는 이 값에서 유도한다
 * @param paymentType    결제 유형 — 이 이벤트에서는 항상 최초 결제
 * @param amount         실제 결제된 금액(USD 센트). invoice에 없으면 null
 * @param currency       통화 — USD 단일이지만 invoice가 준 값을 그대로 싣는다
 * @param paidAt         Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionPaymentSucceededEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        SubscriptionPaymentType paymentType,
        Long amount,
        String currency,
        Instant paidAt,
        String stripeEventId
) {
}
