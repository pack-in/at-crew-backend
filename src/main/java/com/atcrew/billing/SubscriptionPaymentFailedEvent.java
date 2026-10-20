package com.atcrew.billing;

import java.time.Instant;

/**
 * 정기 결제 실패(설정-R03). billing은 즉시 스타터로 내리고 이 이벤트를 발행한다. 안내 메일은 billing의
 * {@code BillingNotificationListener}가 이 이벤트를 받아 커밋 뒤에 보낸다.
 *
 * @param plan 실패 시점에 이용 중이던 프로 플랜
 */
public record SubscriptionPaymentFailedEvent(String memberId, PlanType plan, Instant failedAt) {
}
