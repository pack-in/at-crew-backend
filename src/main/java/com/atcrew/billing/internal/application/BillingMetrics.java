package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 결제 관련 지표(docs/design/observability-design.md §6).
 *
 * <p>웹훅 엔드포인트는 이벤트 종류와 무관하게 하나라, HTTP 지표로는 "결제가 성사되고 있는지"를
 * 알 수 없다. 매출이 멈춘 것을 사용자 문의로 알게 되는 상황을 막으려고 결제 성사·실패를 직접 센다.
 */
@Component
class BillingMetrics {

    private final MeterRegistry registry;

    BillingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void checkoutCompleted(BillingProduct product) {
        Counter.builder("atcrew.billing.checkout.completed")
                .description("Stripe Checkout 결제 완료 건수")
                .tag("product", product.name())
                .register(registry)
                .increment();
    }

    @EventListener
    void onPaymentFailed(SubscriptionPaymentFailedEvent event) {
        Counter.builder("atcrew.billing.subscription.payment.failed")
                .description("구독 정기 결제 실패 건수")
                .tag("plan", event.plan().name())
                .register(registry)
                .increment();
    }
}
