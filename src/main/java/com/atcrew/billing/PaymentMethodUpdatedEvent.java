package com.atcrew.billing;

import java.time.Instant;

/**
 * 회원이 결제 수단을 새로 등록함(PA-09). 카드 번호·브랜드·만료일 등 결제 수단 자체의 정보는
 * 설계상 싣지 않는다 — 집계에 필요한 건 "결제 수단을 바꿨다"는 사건과 그 시점의 구독 상태뿐이다.
 *
 * @param memberId           결제 수단을 등록한 회원
 * @param subscriptionStatus 등록 시점의 살아 있는 구독 상태. 구독이 없으면 null
 * @param updatedAt          Stripe 이벤트 발생 시각(UTC)
 * @param stripeEventId      Stripe 이벤트 ID — 중복 집계 방지용 식별자
 */
public record PaymentMethodUpdatedEvent(
        String memberId,
        SubscriptionStatus subscriptionStatus,
        Instant updatedAt,
        String stripeEventId
) {
}
