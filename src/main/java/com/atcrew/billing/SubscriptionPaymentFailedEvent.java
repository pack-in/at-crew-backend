package com.atcrew.billing;

import java.time.Instant;

/**
 * 정기 결제 실패(설정-R03). billing은 즉시 스타터로 내리고 이 이벤트만 발행하며, 이메일 발송은 하지 않는다 —
 * 메일 발송 인프라가 준비되면 해당 모듈이 구독해서 알림을 보낸다.
 *
 * @param plan 실패 시점에 이용 중이던 프로 플랜
 */
public record SubscriptionPaymentFailedEvent(String memberId, PlanType plan, Instant failedAt) {
}
