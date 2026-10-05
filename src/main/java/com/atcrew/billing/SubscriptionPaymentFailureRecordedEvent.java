package com.atcrew.billing;

import java.time.Instant;

/**
 * 구독 결제 실패를 집계용으로 기록한다(PA-09). 같은 사건의 알림용 이벤트인
 * {@link SubscriptionPaymentFailedEvent}와 함께 발행되며, 둘을 나눠 둔 이유는 수신자가 다르기 때문이다 —
 * 알림용은 메일 발송 모듈이, 이쪽은 분석 모듈이 구독한다. 알림 계약에 집계용 속성을 섞지 않는다.
 *
 * @param memberId       결제가 실패한 회원
 * @param subscriptionId Stripe 구독 ID
 * @param plan           실패 시점에 이용 중이던 프로 플랜
 * @param paymentType    최초 결제인지 정기 갱신인지. billing_reason으로 판정하지 못하면 null
 * @param failureCode    Stripe가 준 실패 코드. invoice에 실패 정보가 없으면 null
 * @param amount         청구하려던 금액(USD 센트). invoice에 없으면 null
 * @param currency       통화 — USD 단일이지만 invoice가 준 값을 그대로 싣는다
 * @param failedAt       Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId  Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionPaymentFailureRecordedEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        SubscriptionPaymentType paymentType,
        String failureCode,
        Long amount,
        String currency,
        Instant failedAt,
        String stripeEventId
) {
}
