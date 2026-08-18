package com.atcrew.billing.internal.config;

import com.atcrew.billing.BillingProduct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 상품 카탈로그 설정. Stripe Dashboard에서 만든 Price ID와 카탈로그 표시용 금액을 묶는다.
 *
 * <p>실제 청구액의 정본은 Stripe의 Price이고 여기 amount는 요금제 페이지 표시용이다 —
 * Dashboard에서 가격을 바꿀 때 이 설정도 함께 갱신해야 한다.
 */
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(String frontendBaseUrl, Map<BillingProduct, Product> products) {

    /** 통화는 USD 단일. 금액은 전부 센트 단위 정수다($5.99 = 599). */
    public static final String CURRENCY = "USD";

    /**
     * @param priceId    Stripe Price ID. 비어 있으면 해당 상품 결제를 시작할 수 없다
     * @param amount     청구 금액(센트)
     * @param listAmount 취소선 정가(센트). 할인이 없으면 null
     */
    public record Product(String priceId, long amount, Long listAmount) {
    }

    public Product product(BillingProduct product) {
        Product config = products == null ? null : products.get(product);
        if (config == null) {
            throw new IllegalStateException("상품 설정이 없습니다: " + product);
        }
        return config;
    }
}
