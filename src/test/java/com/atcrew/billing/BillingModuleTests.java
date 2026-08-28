package com.atcrew.billing;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import com.atcrew.billing.internal.application.BillingWebhookService;
import com.atcrew.billing.internal.domain.BillingCustomer;
import com.atcrew.billing.internal.persistence.BillingCustomerRepository;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.member.MemberService;
import com.atcrew.support.BillingTestSupport;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제/구독 모듈 테스트. 실제 Stripe 서버는 호출하지 않고 웹훅 페이로드 픽스처로 검증한다 —
 * sandbox 실호출 검증은 {@code @Tag("stripe-sandbox")} 테스트와 수동 체크리스트가 담당한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class BillingModuleTests {

    private static final long BASE_CREATED = 1_760_000_000L;

    @Autowired
    BillingService billingService;

    @Autowired
    BillingWebhookService webhookService;

    @Autowired
    MemberService memberService;

    @Autowired
    BillingCustomerRepository customerRepository;

    @Autowired
    EntitlementBalanceRepository balanceRepository;

    // === 단건 게시 상품 ===

    @Test
    void 팀원모집글_결제_웹훅은_업로드권한과_끌어올리기를_함께_지급한다() {
        String memberId = registerMember("grant");

        webhookService.handle(checkoutCompleted("evt_grant_1", memberId, BillingProduct.TEAM_POSTING, "pi_grant_1"));

        assertThat(billingService.getBalance(memberId, BillingProduct.TEAM_POSTING)).isEqualTo(1);
        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isEqualTo(1);
    }

    @Test
    void 같은_웹훅이_재전송돼도_한_번만_반영된다() {
        String memberId = registerMember("idem");
        Event event = checkoutCompleted("evt_idem_1", memberId, BillingProduct.BOOST, "pi_idem_1");

        webhookService.handle(event);
        webhookService.handle(event);

        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isEqualTo(1);
    }

    @Test
    void 잔량이_없으면_차감이_거부된다() {
        String memberId = registerMember("empty");

        assertThatThrownBy(() -> billingService.consume(memberId, BillingProduct.BOOST, "posting-1"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ENTITLEMENT_REQUIRED");
    }

    @Test
    void 잔량_한_개를_두_요청이_동시에_차감하면_한_건만_성공한다() throws Exception {
        String memberId = registerMember("race");
        BillingTestSupport.grant(balanceRepository, memberId, BillingProduct.BOOST, 1);

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> attemptConsume(start, memberId, "posting-a", succeeded)),
                    executor.submit(() -> attemptConsume(start, memberId, "posting-b", succeeded)));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isZero();
    }

    @Test
    void 구독_상품은_보유개수_조회_대상이_아니다() {
        String memberId = registerMember("invalid-product");

        assertThatThrownBy(() -> billingService.getBalance(memberId, BillingProduct.PRO_MONTHLY))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("INVALID_PRODUCT");
    }

    // === 구독 ===

    @Test
    void 구독_생성_웹훅이_프로플랜을_부여한다() {
        String memberId = registerMemberWithCustomer("sub-create", "cus_sub_create");

        webhookService.handle(subscriptionEvent("evt_sub_1", "customer.subscription.created",
                "sub_create", "cus_sub_create", "active", BASE_CREATED));

        assertThat(billingService.hasProPlan(memberId)).isTrue();
        BillingSummaryInfo summary = billingService.getSummary(memberId);
        assertThat(summary.plan()).isEqualTo(PlanType.PRO_MONTHLY);
        assertThat(summary.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(summary.currentPeriodEnd()).isNotNull();
    }

    @Test
    void 결제_실패_웹훅은_유예_없이_스타터로_내리고_알림_이벤트를_발행한다(PublishedEvents events) {
        String memberId = registerMemberWithCustomer("sub-fail", "cus_sub_fail");
        webhookService.handle(subscriptionEvent("evt_fail_sub", "customer.subscription.created",
                "sub_fail", "cus_sub_fail", "active", BASE_CREATED));

        webhookService.handle(invoiceEvent("evt_fail_invoice", "invoice.payment_failed",
                "cus_sub_fail", BASE_CREATED + 10));

        assertThat(billingService.hasProPlan(memberId)).isFalse();
        BillingSummaryInfo summary = billingService.getSummary(memberId);
        assertThat(summary.plan()).isEqualTo(PlanType.STARTER);
        assertThat(summary.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(events.ofType(SubscriptionPaymentFailedEvent.class)
                .matching(event -> memberId.equals(event.memberId())))
                .hasSize(1);
    }

    @Test
    void 재결제_성공_웹훅이_플랜을_복원한다() {
        String memberId = registerMemberWithCustomer("sub-recover", "cus_sub_recover");
        webhookService.handle(subscriptionEvent("evt_recover_sub", "customer.subscription.created",
                "sub_recover", "cus_sub_recover", "active", BASE_CREATED));
        webhookService.handle(invoiceEvent("evt_recover_failed", "invoice.payment_failed",
                "cus_sub_recover", BASE_CREATED + 10));

        webhookService.handle(invoiceEvent("evt_recover_paid", "invoice.payment_succeeded",
                "cus_sub_recover", BASE_CREATED + 20));

        assertThat(billingService.hasProPlan(memberId)).isTrue();
        assertThat(billingService.getSummary(memberId).plan()).isEqualTo(PlanType.PRO_MONTHLY);
    }

    @Test
    void 구독_취소_웹훅은_즉시_스타터로_내린다() {
        String memberId = registerMemberWithCustomer("sub-cancel", "cus_sub_cancel");
        webhookService.handle(subscriptionEvent("evt_cancel_created", "customer.subscription.created",
                "sub_cancel", "cus_sub_cancel", "active", BASE_CREATED));

        webhookService.handle(subscriptionEvent("evt_cancel_deleted", "customer.subscription.deleted",
                "sub_cancel", "cus_sub_cancel", "canceled", BASE_CREATED + 10));

        assertThat(billingService.hasProPlan(memberId)).isFalse();
        assertThat(billingService.getSummary(memberId).plan()).isEqualTo(PlanType.STARTER);
    }

    @Test
    void 순서가_뒤바뀐_구독_웹훅은_무시된다() {
        String memberId = registerMemberWithCustomer("sub-order", "cus_sub_order");
        webhookService.handle(subscriptionEvent("evt_order_new", "customer.subscription.updated",
                "sub_order", "cus_sub_order", "active", BASE_CREATED + 100));

        // 먼저 발생했지만 늦게 도착한 취소 이벤트
        webhookService.handle(subscriptionEvent("evt_order_old", "customer.subscription.deleted",
                "sub_order", "cus_sub_order", "canceled", BASE_CREATED));

        assertThat(billingService.hasProPlan(memberId)).isTrue();
    }

    // === 환불 ===

    @Test
    void 환불_웹훅은_지급된_권한을_회수한다() {
        String memberId = registerMember("refund");
        webhookService.handle(checkoutCompleted("evt_refund_pay", memberId, BillingProduct.BOOST, "pi_refund_1"));

        webhookService.handle(chargeRefunded("evt_refund_back", "pi_refund_1", true));

        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isZero();
    }

    @Test
    void 이미_사용한_권한은_환불로_회수하지_않는다() {
        String memberId = registerMember("refund-used");
        webhookService.handle(checkoutCompleted("evt_used_pay", memberId, BillingProduct.BOOST, "pi_used_1"));
        billingService.consume(memberId, BillingProduct.BOOST, "posting-used");

        webhookService.handle(chargeRefunded("evt_used_back", "pi_used_1", true));

        // 음수로 내려가지 않는다 — 이후 구매분이 소급 차감되면 안 된다
        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isZero();
    }

    @Test
    void 부분_환불은_권한을_회수하지_않는다() {
        String memberId = registerMember("refund-partial");
        webhookService.handle(checkoutCompleted("evt_partial_pay", memberId, BillingProduct.BOOST, "pi_partial_1"));

        webhookService.handle(chargeRefunded("evt_partial_back", "pi_partial_1", false));

        assertThat(billingService.getBalance(memberId, BillingProduct.BOOST)).isEqualTo(1);
    }

    // === 카탈로그 ===

    @Test
    void 카탈로그는_프로_월간_연간_두_종만_내려준다() {
        // PH-09(2026-08-23): 연간 결제 재개 — 단건 상품 3종은 여전히 판매 중단이라 카탈로그에서 빠진다.
        List<CatalogItemInfo> catalog = billingService.getCatalog(null);

        assertThat(catalog).hasSize(2);
        CatalogItemInfo proMonthly = catalog.stream()
                .filter(item -> item.product() == BillingProduct.PRO_MONTHLY)
                .findFirst().orElseThrow();
        assertThat(proMonthly.amount()).isEqualTo(800);
        assertThat(proMonthly.listAmount()).isNull();
        assertThat(proMonthly.cta()).isEqualTo(CatalogItemInfo.CtaState.AVAILABLE);
        assertThat(proMonthly.currency()).isEqualTo("USD");

        CatalogItemInfo proYearly = catalog.stream()
                .filter(item -> item.product() == BillingProduct.PRO_YEARLY)
                .findFirst().orElseThrow();
        assertThat(proYearly.amount()).isEqualTo(8000);
        assertThat(proYearly.listAmount()).isEqualTo(9600);
        assertThat(proYearly.cta()).isEqualTo(CatalogItemInfo.CtaState.AVAILABLE);
        assertThat(proYearly.currency()).isEqualTo("USD");
    }

    @Test
    void 이용중인_플랜은_CURRENT로_내려가고_다른_주기는_CHANGE로_내려간다() {
        String memberId = registerMemberWithCustomer("catalog-pro", "cus_catalog_pro");
        webhookService.handle(subscriptionEvent("evt_catalog_pro", "customer.subscription.created",
                "sub_catalog", "cus_catalog_pro", "active", BASE_CREATED));

        List<CatalogItemInfo> catalog = billingService.getCatalog(memberId);

        assertThat(ctaOf(catalog, BillingProduct.PRO_MONTHLY)).isEqualTo(CatalogItemInfo.CtaState.CURRENT);
        assertThat(ctaOf(catalog, BillingProduct.PRO_YEARLY)).isEqualTo(CatalogItemInfo.CtaState.CHANGE);
    }

    @Test
    void 판매_중단_상품은_Checkout_생성이_막힌다() {
        // PH-08: 카탈로그에서 숨겨도 API 직접 호출로 우회 구매하는 것까지 막는다.
        String memberId = registerMember("disabled-checkout");

        assertThatThrownBy(() -> billingService.createCheckoutSession(memberId, BillingProduct.TEAM_POSTING))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PRICE_NOT_CONFIGURED");
    }

    // === 헬퍼 ===

    private void attemptConsume(CountDownLatch start, String memberId, String refId, AtomicInteger succeeded) {
        try {
            start.await();
            billingService.consume(memberId, BillingProduct.BOOST, refId);
            succeeded.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // 잔량 부족 또는 낙관적 락 충돌 — 경합에서 밀린 쪽이다
        }
    }

    private CatalogItemInfo.CtaState ctaOf(List<CatalogItemInfo> catalog, BillingProduct product) {
        return catalog.stream()
                .filter(item -> item.product() == product)
                .findFirst()
                .orElseThrow()
                .cta();
    }

    private String registerMember(String handlePrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberService.register(handlePrefix + "-" + suffix + "@atcrew.com",
                handlePrefix + suffix, "테스터").id();
    }

    private String registerMemberWithCustomer(String handlePrefix, String stripeCustomerId) {
        String memberId = registerMember(handlePrefix);
        customerRepository.save(BillingCustomer.create(memberId, stripeCustomerId));
        return memberId;
    }

    private Event checkoutCompleted(String eventId, String memberId, BillingProduct product,
            String paymentIntentId) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_%s","object":"checkout.session","mode":"payment",
                 "payment_intent":"%s","metadata":{"memberId":"%s","product":"%s"}}}}
                """.formatted(eventId, Stripe.API_VERSION, BASE_CREATED, eventId, paymentIntentId, memberId, product.name());
        return Webhook.constructEventWithoutVerification(json);
    }

    private Event subscriptionEvent(String eventId, String type, String subscriptionId,
            String customerId, String status, long created) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"%s",
                 "data":{"object":{"id":"%s","object":"subscription","customer":"%s","status":"%s",
                 "cancel_at_period_end":false,
                 "items":{"object":"list","data":[{"id":"si_1","object":"subscription_item",
                 "current_period_end":%d,"price":{"id":"price_test_pro_monthly","object":"price"}}]}}}}
                """.formatted(eventId, Stripe.API_VERSION, created, type, subscriptionId, customerId, status,
                created + 2_592_000L);
        return Webhook.constructEventWithoutVerification(json);
    }

    private Event invoiceEvent(String eventId, String type, String customerId, long created) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"%s",
                 "data":{"object":{"id":"in_%s","object":"invoice","customer":"%s"}}}
                """.formatted(eventId, Stripe.API_VERSION, created, type, eventId, customerId);
        return Webhook.constructEventWithoutVerification(json);
    }

    private Event chargeRefunded(String eventId, String paymentIntentId, boolean fullyRefunded) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"charge.refunded",
                 "data":{"object":{"id":"ch_%s","object":"charge","payment_intent":"%s","refunded":%s}}}
                """.formatted(eventId, Stripe.API_VERSION, BASE_CREATED + 30, eventId, paymentIntentId, fullyRefunded);
        return Webhook.constructEventWithoutVerification(json);
    }
}
