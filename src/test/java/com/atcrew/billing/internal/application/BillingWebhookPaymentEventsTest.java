package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PaymentMethodUpdatedEvent;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import com.atcrew.billing.SubscriptionPaymentFailureRecordedEvent;
import com.atcrew.billing.SubscriptionPaymentRecoveredEvent;
import com.atcrew.billing.SubscriptionPaymentSucceededEvent;
import com.atcrew.billing.SubscriptionPaymentType;
import com.atcrew.billing.SubscriptionRenewedEvent;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.billing.internal.domain.BillingCustomer;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.domain.WebhookEvent;
import com.atcrew.billing.internal.persistence.BillingCustomerRepository;
import com.atcrew.billing.internal.persistence.EntitlementLedgerRepository;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.billing.internal.persistence.WebhookEventRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * invoice 결제 웹훅과 결제 수단 등록 웹훅이 어떤 분석 이벤트를 내는지 고정한다(PA-09).
 *
 * <p>DB 없이 저장소를 목으로 세운다 — 결제 유형 판정은 billing_reason과 {@code sync()} 호출 전
 * 구독 상태만 보므로, 영속화까지 가지 않고도 분기를 전부 덮을 수 있다. 카운터 증가는 dirty checking에
 * 맡기므로 저장 호출이 아니라 엔티티 값 변화로 확인한다.
 */
class BillingWebhookPaymentEventsTest {

    private static final String MEMBER_ID = "member-1";
    private static final String CUSTOMER_ID = "cus_1";
    private static final String SUBSCRIPTION_ID = "sub_1";
    private static final long BASE_CREATED = 1_760_000_000L;
    private static final Instant OCCURRED_AT = Instant.ofEpochSecond(BASE_CREATED);
    private static final Instant PERIOD_END = Instant.ofEpochSecond(BASE_CREATED + 2_592_000L);
    private static final long AMOUNT = 1_200L;

    private final WebhookEventRepository webhookEventRepository = mock(WebhookEventRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final BillingWebhookService service = new BillingWebhookService(webhookEventRepository,
            subscriptionRepository, customerRepository, mock(EntitlementLedgerRepository.class),
            mock(EntitlementService.class), properties(), eventPublisher, mock(BillingMetrics.class));

    @BeforeEach
    void setUp() {
        when(webhookEventRepository.existsById(anyString())).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(customerRepository.findByStripeCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(BillingCustomer.create(MEMBER_ID, CUSTOMER_ID)));
    }

    @Test
    void 최초_결제_성공은_결제_성공_이벤트를_낸다() {
        givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_1", "invoice.payment_succeeded", "subscription_create", null));

        SubscriptionPaymentSucceededEvent published = captureOnly(SubscriptionPaymentSucceededEvent.class);
        assertThat(published.memberId()).isEqualTo(MEMBER_ID);
        assertThat(published.subscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(published.paymentType()).isEqualTo(SubscriptionPaymentType.INITIAL);
        assertThat(published.amount()).isEqualTo(AMOUNT);
        assertThat(published.currency()).isEqualTo("USD");
        assertThat(published.paidAt()).isEqualTo(OCCURRED_AT);
        assertThat(published.stripeEventId()).isEqualTo("evt_1");
    }

    @Test
    void 정기_갱신_결제_성공은_갱신_이벤트와_함께_갱신_횟수를_올린다() {
        Subscription mirror = givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_2", "invoice.payment_succeeded", "subscription_cycle", null));

        SubscriptionRenewedEvent published = captureOnly(SubscriptionRenewedEvent.class);
        assertThat(published.renewalNumber()).isEqualTo(1);
        assertThat(published.plan()).isEqualTo(PlanType.PRO_MONTHLY);
        assertThat(published.amount()).isEqualTo(AMOUNT);
        assertThat(mirror.getRenewalNumber()).isEqualTo(1);
        assertThat(mirror.getRecoveryAttemptNumber()).isZero();
    }

    @Test
    void 복구_대기였던_구독의_결제_성공은_복구_이벤트와_함께_복구_횟수를_올린다() {
        Subscription mirror = givenMirror(SubscriptionStatus.PAST_DUE);

        service.handle(invoiceEvent("evt_3", "invoice.payment_succeeded", "subscription_cycle", null));

        SubscriptionPaymentRecoveredEvent published = captureOnly(SubscriptionPaymentRecoveredEvent.class);
        assertThat(published.recoveryAttemptNumber()).isEqualTo(1);
        assertThat(published.recoveredAt()).isEqualTo(OCCURRED_AT);
        assertThat(mirror.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(mirror.getRecoveryAttemptNumber()).isEqualTo(1);
        assertThat(mirror.getRenewalNumber()).isZero();
    }

    @Test
    void 플랜_변경_비례배분_청구는_결제_이벤트를_내지_않는다() {
        Subscription mirror = givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_4", "invoice.payment_succeeded", "subscription_update", null));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertThat(mirror.getRenewalNumber()).isZero();
    }

    @Test
    void 미러에_없는_구독의_결제_성공은_이벤트를_내지_않는다() {
        when(subscriptionRepository.findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(anyString(), any()))
                .thenReturn(List.of());

        service.handle(invoiceEvent("evt_5", "invoice.payment_succeeded", "subscription_cycle", null));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void 결제_실패는_알림_이벤트와_집계_이벤트를_함께_낸다() {
        Subscription mirror = givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_6", "invoice.payment_failed", "subscription_cycle", null));

        assertThat(published()).hasAtLeastOneElementOfType(SubscriptionPaymentFailedEvent.class);
        SubscriptionPaymentFailureRecordedEvent recorded =
                capture(SubscriptionPaymentFailureRecordedEvent.class);
        assertThat(recorded.memberId()).isEqualTo(MEMBER_ID);
        assertThat(recorded.subscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(recorded.paymentType()).isEqualTo(SubscriptionPaymentType.RENEWAL);
        assertThat(recorded.amount()).isEqualTo(AMOUNT);
        assertThat(recorded.currency()).isEqualTo("USD");
        assertThat(recorded.failedAt()).isEqualTo(OCCURRED_AT);
        assertThat(recorded.stripeEventId()).isEqualTo("evt_6");
        assertThat(mirror.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void 결제_실패에_실패_코드가_없으면_코드를_비워_보낸다() {
        givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_7", "invoice.payment_failed", "subscription_create", null));

        SubscriptionPaymentFailureRecordedEvent recorded =
                capture(SubscriptionPaymentFailureRecordedEvent.class);
        assertThat(recorded.failureCode()).isNull();
        assertThat(recorded.paymentType()).isEqualTo(SubscriptionPaymentType.INITIAL);
    }

    @Test
    void 결제_실패에_거절_코드가_실려오면_그것을_쓴다() {
        givenMirror(SubscriptionStatus.ACTIVE);

        service.handle(invoiceEvent("evt_8", "invoice.payment_failed", "subscription_cycle",
                """
                        {"code":"card_declined","decline_code":"insufficient_funds"}"""));

        assertThat(capture(SubscriptionPaymentFailureRecordedEvent.class).failureCode())
                .isEqualTo("insufficient_funds");
    }

    @Test
    void 결제_수단_등록은_구독_상태만_실어_보낸다() {
        givenMirror(SubscriptionStatus.PAST_DUE);

        service.handle(paymentMethodEvent("evt_9", CUSTOMER_ID));

        PaymentMethodUpdatedEvent published = captureOnly(PaymentMethodUpdatedEvent.class);
        assertThat(published.memberId()).isEqualTo(MEMBER_ID);
        assertThat(published.subscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(published.updatedAt()).isEqualTo(OCCURRED_AT);
        assertThat(published.stripeEventId()).isEqualTo("evt_9");
    }

    @Test
    void 구독이_없는_회원의_결제_수단_등록도_이벤트를_낸다() {
        when(subscriptionRepository.findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(anyString(), any()))
                .thenReturn(List.of());

        service.handle(paymentMethodEvent("evt_10", CUSTOMER_ID));

        assertThat(captureOnly(PaymentMethodUpdatedEvent.class).subscriptionStatus()).isNull();
    }

    @Test
    void 매핑된_회원이_없는_결제_수단_등록은_이벤트를_내지_않는다() {
        service.handle(paymentMethodEvent("evt_11", "cus_unknown"));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // === 헬퍼 ===

    /** 발행된 이벤트 전부. */
    private List<Object> published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    /** 발행된 이벤트가 기대 타입 하나뿐임을 확인하고 그 값을 돌려준다 — 결제 분기는 서로 배타적이다. */
    private <T> T captureOnly(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(type);
        return type.cast(captor.getValue());
    }

    /** 발행된 이벤트 중 기대 타입 하나를 꺼낸다(실패처럼 알림용과 집계용이 함께 나오는 경우). */
    private <T> T capture(Class<T> type) {
        List<Object> matched = published().stream().filter(type::isInstance).toList();
        assertThat(matched).hasSize(1);
        return type.cast(matched.getFirst());
    }

    private Subscription givenMirror(SubscriptionStatus status) {
        Subscription mirror = Subscription.create(MEMBER_ID, SUBSCRIPTION_ID, PlanType.PRO_MONTHLY,
                status, PERIOD_END, false, OCCURRED_AT.minusSeconds(100));
        when(subscriptionRepository.findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(anyString(), any()))
                .thenReturn(List.of(mirror));
        return mirror;
    }

    private Event invoiceEvent(String eventId, String type, String billingReason,
            String finalizationErrorJson) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"%s",
                 "data":{"object":{"id":"in_1","object":"invoice","customer":"%s",
                 "billing_reason":"%s","amount_paid":%d,"amount_due":%d,"currency":"usd"%s}}}
                """.formatted(eventId, Stripe.API_VERSION, BASE_CREATED, type, CUSTOMER_ID,
                billingReason, AMOUNT, AMOUNT,
                finalizationErrorJson == null ? "" : ",\"last_finalization_error\":" + finalizationErrorJson);
        return Webhook.constructEventWithoutVerification(json);
    }

    private Event paymentMethodEvent(String eventId, String customerId) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,
                 "type":"payment_method.attached",
                 "data":{"object":{"id":"pm_1","object":"payment_method","type":"card",
                 "customer":"%s"}}}
                """.formatted(eventId, Stripe.API_VERSION, BASE_CREATED, customerId);
        return Webhook.constructEventWithoutVerification(json);
    }

    private static BillingProperties properties() {
        return new BillingProperties("http://localhost:3000", 30,
                Arrays.stream(BillingProduct.values()).collect(Collectors.toMap(Function.identity(),
                        product -> new BillingProperties.Product("price_" + product.name(), 100, null, true))));
    }
}
