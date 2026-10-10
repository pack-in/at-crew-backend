package com.atcrew.billing.internal.infra;

/**
 * 방금 만든 Stripe Checkout 세션. 프론트에는 URL만 내려가지만, 결제창 도달을 집계하려면
 * 세션 ID도 필요해 함께 들고 나온다(PA-08).
 *
 * @param id  Stripe Checkout 세션 ID
 * @param url 결제 페이지 URL
 */
public record CheckoutSession(String id, String url) {
}
