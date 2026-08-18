package com.atcrew.billing.sandbox;

import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.testhelpers.TestClock;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.testhelpers.TestClockAdvanceParams;
import com.stripe.param.testhelpers.TestClockCreateParams;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe sandbox 실호출 테스트. 실제 Stripe test mode API를 부르고 test clock으로 시간을 앞당겨
 * 정기 결제 갱신·실패를 재현한다.
 *
 * <p>기본 {@code ./gradlew test}에서는 태그로 제외되며, {@code ./gradlew stripeSandboxTest}로만 실행한다.
 * 키가 없으면 자동으로 건너뛴다 — CI에는 sandbox 키를 주입하지 않는다(D19).
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 웹훅 수신 이후의 동작은 {@code BillingModuleTests}가 픽스처로 검증하고,
 * 여기서는 "Stripe가 우리가 기대한 대로 청구/실패시키는가"만 확인한다.
 */
@Tag("stripe-sandbox")
class StripeSandboxTest {

    private static final String VISA = "pm_card_visa";
    /** 결제가 거절되는 테스트 카드 — 정기 결제 실패 재현용. */
    private static final String DECLINED = "pm_card_chargeDeclined";

    private static StripeClient client;
    private static String monthlyPriceId;

    @BeforeAll
    static void setUp() {
        String secretKey = System.getenv("STRIPE_SECRET_KEY");
        monthlyPriceId = System.getenv("STRIPE_PRICE_PRO_MONTHLY");
        Assumptions.assumeTrue(secretKey != null && secretKey.startsWith("sk_test_"),
                "STRIPE_SECRET_KEY(test mode)가 없어 sandbox 테스트를 건너뜁니다");
        Assumptions.assumeTrue(monthlyPriceId != null && !monthlyPriceId.isBlank(),
                "STRIPE_PRICE_PRO_MONTHLY가 없어 sandbox 테스트를 건너뜁니다");
        client = StripeClient.builder().setApiKey(secretKey).build();
    }

    @Test
    void 한_달을_앞당기면_구독이_갱신된다() throws Exception {
        TestClock clock = createClock();
        Customer customer = createCustomerOnClock(clock, VISA);
        Subscription subscription = createSubscription(customer);

        assertThat(subscription.getStatus()).isEqualTo("active");

        advanceBy(clock, 32 * 24 * 60 * 60);

        Subscription renewed = client.subscriptions().retrieve(subscription.getId());
        assertThat(renewed.getStatus()).isEqualTo("active");
    }

    @Test
    void 결제가_거절되면_구독이_past_due로_바뀐다() throws Exception {
        TestClock clock = createClock();
        Customer customer = createCustomerOnClock(clock, DECLINED);
        Subscription subscription = createSubscription(customer);

        advanceBy(clock, 32 * 24 * 60 * 60);

        Subscription failed = client.subscriptions().retrieve(subscription.getId());
        assertThat(failed.getStatus()).isIn("past_due", "unpaid", "canceled", "incomplete");
    }

    private TestClock createClock() throws Exception {
        return client.testHelpers().testClocks().create(TestClockCreateParams.builder()
                .setFrozenTime(Instant.now().getEpochSecond())
                .setName("at-crew-sandbox")
                .build());
    }

    private Customer createCustomerOnClock(TestClock clock, String paymentMethod) throws Exception {
        return client.customers().create(CustomerCreateParams.builder()
                .setTestClock(clock.getId())
                .setPaymentMethod(paymentMethod)
                .setInvoiceSettings(CustomerCreateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethod)
                        .build())
                .build());
    }

    private Subscription createSubscription(Customer customer) throws Exception {
        return client.subscriptions().create(SubscriptionCreateParams.builder()
                .setCustomer(customer.getId())
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(monthlyPriceId).build())
                .build());
    }

    /** test clock 전진은 비동기라 상태가 ready가 될 때까지 기다린다. */
    private void advanceBy(TestClock clock, long seconds) throws Exception {
        client.testHelpers().testClocks().advance(clock.getId(), TestClockAdvanceParams.builder()
                .setFrozenTime(clock.getFrozenTime() + seconds)
                .build());

        for (int attempt = 0; attempt < 60; attempt++) {
            TestClock current = client.testHelpers().testClocks().retrieve(clock.getId());
            if ("ready".equals(current.getStatus())) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("test clock 전진이 60초 안에 끝나지 않았습니다");
    }
}
