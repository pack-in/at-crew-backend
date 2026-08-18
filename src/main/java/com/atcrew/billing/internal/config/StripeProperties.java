package com.atcrew.billing.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe 접속 시크릿. 값이 비어 있어도 애플리케이션은 기동되며, 결제 API를 실제로 호출할 때 실패한다.
 *
 * <p>publishableKey는 현재 구성(호스팅 Checkout)에서 백엔드가 쓰지 않지만 프론트 전달용으로 함께 보관한다.
 */
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(String secretKey, String publishableKey, String webhookSecret) {
}
