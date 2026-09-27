package com.atcrew.billing.internal.infra;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.internal.config.BillingProperties;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkout 세션 파라미터 조립 검증. Stripe를 호출하지 않으므로 컨테이너·컨텍스트 없이 끝난다 —
 * 실제 세션 생성은 {@code @Tag("stripe-sandbox")} 테스트와 수동 체크리스트가 담당한다.
 */
class StripeGatewayCheckoutParamsTest {

    private final StripeGateway gateway = new StripeGateway(null, new BillingProperties(
            "http://localhost:3000", 30,
            Arrays.stream(BillingProduct.values()).collect(Collectors.toMap(Function.identity(),
                    product -> new BillingProperties.Product("price_" + product.name(), 100, null, true)))));

    @Test
    void 체험_일수가_있으면_구독_세션에_trial_period_days가_붙는다() {
        SessionCreateParams params = gateway.checkoutParams("member-1", BillingProduct.PRO_YEARLY, "cus_1", 30);

        assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
        assertThat(params.getSubscriptionData().getTrialPeriodDays()).isEqualTo(30L);
        // Managed Payments는 payment_method_types를 받지 않는다 — 체험을 붙여도 넣지 않는다.
        assertThat(params.getPaymentMethodTypes()).isNull();
        assertThat(params.getManagedPayments().getEnabled()).isTrue();
    }

    @Test
    void 체험_일수가_0이면_subscription_data를_보내지_않는다() {
        SessionCreateParams params = gateway.checkoutParams("member-1", BillingProduct.PRO_MONTHLY, "cus_1", 0);

        assertThat(params.getSubscriptionData()).isNull();
    }

    @Test
    void 단건_결제에는_체험을_붙이지_않는다() {
        SessionCreateParams params = gateway.checkoutParams("member-1", BillingProduct.BOOST, "cus_1", 30);

        assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
        assertThat(params.getSubscriptionData()).isNull();
    }
}
