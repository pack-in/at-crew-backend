package com.atcrew.billing;

import java.time.Instant;

/**
 * Checkout 세션 생성 성공(PA-08). 결제창까지 도달한 시도를 집계하기 위한 이벤트이며,
 * 실제 결제 성사는 웹훅이 정본이다(D16).
 *
 * @param memberId          결제를 시작한 회원
 * @param checkoutSessionId Stripe Checkout 세션 ID — 이 사건을 특정하는 식별자
 * @param product           결제 대상 상품. 구독 2종은 주기까지 구분된다
 * @param trialDays         세션에 붙은 무료 체험 일수. 체험이 없으면 0
 * @param startedAt         세션을 만든 시각(UTC)
 */
public record SubscriptionCheckoutStartedEvent(
        String memberId,
        String checkoutSessionId,
        BillingProduct product,
        int trialDays,
        Instant startedAt
) {
}
