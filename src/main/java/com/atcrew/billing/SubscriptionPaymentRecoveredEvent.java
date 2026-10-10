package com.atcrew.billing;

import java.time.Instant;

/**
 * 결제 실패로 복구 대기(PAST_DUE)였던 구독의 재결제 성공(PA-09). 혜택이 다시 열린 사건이므로
 * 정상 갱신({@link SubscriptionRenewedEvent})과 구분해 집계한다.
 *
 * @param memberId               결제한 회원
 * @param subscriptionId         Stripe 구독 ID
 * @param plan                   복구된 프로 플랜. 결제 주기는 이 값에서 유도한다
 * @param recoveryAttemptNumber  이번이 몇 번째 복구인지. 카운터 도입(V49) 이후 건만 셈
 * @param amount                 실제 결제된 금액(USD 센트). invoice에 없으면 null
 * @param currency               통화 — USD 단일이지만 invoice가 준 값을 그대로 싣는다
 * @param recoveredAt            Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId          Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record SubscriptionPaymentRecoveredEvent(
        String memberId,
        String subscriptionId,
        PlanType plan,
        int recoveryAttemptNumber,
        Long amount,
        String currency,
        Instant recoveredAt,
        String stripeEventId
) {
}
