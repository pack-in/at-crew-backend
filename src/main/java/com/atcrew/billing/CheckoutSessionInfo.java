package com.atcrew.billing;

/**
 * Stripe Checkout 결제 페이지 URL. 프론트는 이 URL로 이동시키기만 하고 카드 UI를 직접 그리지 않는다.
 */
public record CheckoutSessionInfo(String checkoutUrl) {
}
