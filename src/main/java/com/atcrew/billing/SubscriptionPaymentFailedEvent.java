package com.atcrew.billing;

import java.time.Instant;

/**
 * 정기 결제 실패(설정-R03). billing은 즉시 스타터로 내리고 이 이벤트를 발행한다. 안내 메일은 billing의
 * {@code BillingNotificationListener}가 이 이벤트를 받아 커밋 뒤에 보낸다.
 *
 * <p>Stripe가 같은 인보이스를 재시도하다 또 실패하면 시도마다 이 이벤트가 난다. 실패 횟수 지표는 시도마다
 * 세는 게 맞지만, 회원 안내 메일은 첫 실패에만 보낸다 — 그래서 {@code attemptCount}를 함께 싣는다.
 *
 * @param plan         실패 시점에 이용 중이던 프로 플랜
 * @param attemptCount 이 인보이스의 결제 시도 횟수(Stripe {@code attempt_count}, 첫 시도가 1). 값이 없으면 1로 본다
 */
public record SubscriptionPaymentFailedEvent(String memberId, PlanType plan, Instant failedAt, long attemptCount) {
}
