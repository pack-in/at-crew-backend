package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionActivatedEvent;
import com.atcrew.billing.SubscriptionBillingIntervalChangedEvent;
import com.atcrew.billing.SubscriptionCancelRequestRevokedEvent;
import com.atcrew.billing.SubscriptionCancelRequestedEvent;
import com.atcrew.billing.SubscriptionEndReason;
import com.atcrew.billing.SubscriptionEndedEvent;
import com.atcrew.billing.SubscriptionReactivatedEvent;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 구독 웹훅이 상태 전이별로 어떤 분석 이벤트를 내는지 고정한다(PA-08).
 *
 * <p>DB 없이 저장소를 목으로 세운다 — 상태 전이 판정은 {@code sync()} 호출 전후의 필드 비교만으로
 * 이뤄지므로, 실제 영속화까지 가지 않고도 분기를 전부 덮을 수 있다.
 */
class BillingWebhookSubscriptionEventsTest {

    private static final String MEMBER_ID = "member-1";
    private static final String CUSTOMER_ID = "cus_1";
    private static final String SUBSCRIPTION_ID = "sub_1";
    private static final long BASE_CREATED = 1_760_000_000L;
    private static final Instant OCCURRED_AT = Instant.ofEpochSecond(BASE_CREATED);
    private static final long PERIOD_END = BASE_CREATED + 2_592_000L;
    private static final String MONTHLY_PRICE_ID = "price_PRO_MONTHLY";
    private static final String YEARLY_PRICE_ID = "price_PRO_YEARLY";

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
        when(subscriptionRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByMemberIdAndStatusOrderByStripeUpdatedAtDesc(
                anyString(), any(SubscriptionStatus.class))).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void 과거_구독이_없는_신규_활성_구독은_활성화_이벤트를_낸다() {
        service.handle(subscriptionEvent("evt_1", "customer.subscription.created", "active",
                false, MONTHLY_PRICE_ID, BASE_CREATED));

        SubscriptionActivatedEvent published = captureOnly(SubscriptionActivatedEvent.class);
        assertThat(published.memberId()).isEqualTo(MEMBER_ID);
        assertThat(published.subscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(published.plan()).isEqualTo(PlanType.PRO_MONTHLY);
        assertThat(published.activatedAt()).isEqualTo(OCCURRED_AT);
        assertThat(published.stripeEventId()).isEqualTo("evt_1");
    }

    @Test
    void 결제가_끝나지_않은_신규_구독은_활성화로_세지_않는다() {
        service.handle(subscriptionEvent("evt_2", "customer.subscription.created", "incomplete",
                false, MONTHLY_PRICE_ID, BASE_CREATED));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void 취소_이력이_있는_회원의_신규_구독은_재활성화_이벤트를_낸다() {
        Instant previousPeriodEnd = OCCURRED_AT.minus(Duration.ofDays(10));
        when(subscriptionRepository.findFirstByMemberIdAndStatusOrderByStripeUpdatedAtDesc(
                MEMBER_ID, SubscriptionStatus.CANCELED))
                .thenReturn(Optional.of(mirror(PlanType.PRO_MONTHLY, SubscriptionStatus.CANCELED,
                        previousPeriodEnd, false, previousPeriodEnd)));

        service.handle(subscriptionEvent("evt_3", "customer.subscription.created", "active",
                false, YEARLY_PRICE_ID, BASE_CREATED));

        SubscriptionReactivatedEvent published = captureOnly(SubscriptionReactivatedEvent.class);
        assertThat(published.plan()).isEqualTo(PlanType.PRO_YEARLY);
        assertThat(published.daysSinceEnd()).isEqualTo(10L);
        assertThat(published.stripeEventId()).isEqualTo("evt_3");
    }

    @Test
    void 취소_예약이_켜지면_취소_요청_이벤트를_낸다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, false);

        service.handle(subscriptionEvent("evt_4", "customer.subscription.updated", "active",
                true, MONTHLY_PRICE_ID, BASE_CREATED));

        SubscriptionCancelRequestedEvent published = captureOnly(SubscriptionCancelRequestedEvent.class);
        assertThat(published.currentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(PERIOD_END));
        assertThat(published.plan()).isEqualTo(PlanType.PRO_MONTHLY);
        assertThat(published.stripeEventId()).isEqualTo("evt_4");
    }

    @Test
    void 취소_예약이_꺼지면_취소_철회_이벤트를_낸다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, true);

        service.handle(subscriptionEvent("evt_5", "customer.subscription.updated", "active",
                false, MONTHLY_PRICE_ID, BASE_CREATED));

        SubscriptionCancelRequestRevokedEvent published =
                captureOnly(SubscriptionCancelRequestRevokedEvent.class);
        assertThat(published.currentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(PERIOD_END));
        assertThat(published.stripeEventId()).isEqualTo("evt_5");
    }

    @Test
    void 취소를_예약했던_구독의_종료는_자발적_종료로_판정한다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, true);

        service.handle(subscriptionEvent("evt_6", "customer.subscription.deleted", "canceled",
                true, MONTHLY_PRICE_ID, BASE_CREATED));

        assertThat(captureOnly(SubscriptionEndedEvent.class).endReason())
                .isEqualTo(SubscriptionEndReason.VOLUNTARY);
    }

    @Test
    void 결제_실패로_복구_대기였던_구독의_종료는_결제_실패로_판정한다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.PAST_DUE, false);

        service.handle(subscriptionEvent("evt_7", "customer.subscription.deleted", "canceled",
                false, MONTHLY_PRICE_ID, BASE_CREATED));

        assertThat(captureOnly(SubscriptionEndedEvent.class).endReason())
                .isEqualTo(SubscriptionEndReason.PAYMENT_FAILED);
    }

    @Test
    void 예약도_결제_실패도_없던_구독의_종료는_기타로_판정한다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, false);

        service.handle(subscriptionEvent("evt_8", "customer.subscription.deleted", "canceled",
                false, MONTHLY_PRICE_ID, BASE_CREATED));

        SubscriptionEndedEvent published = captureOnly(SubscriptionEndedEvent.class);
        assertThat(published.endReason()).isEqualTo(SubscriptionEndReason.OTHER);
        assertThat(published.endedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 플랜만_바뀌면_결제_주기_변경_이벤트를_낸다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, false);

        service.handle(subscriptionEvent("evt_9", "customer.subscription.updated", "active",
                false, YEARLY_PRICE_ID, BASE_CREATED));

        SubscriptionBillingIntervalChangedEvent published =
                captureOnly(SubscriptionBillingIntervalChangedEvent.class);
        assertThat(published.previousPlan()).isEqualTo(PlanType.PRO_MONTHLY);
        assertThat(published.newPlan()).isEqualTo(PlanType.PRO_YEARLY);
        assertThat(published.effectiveAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void 취소_예약과_플랜_변경이_겹치면_취소_요청만_낸다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, false);

        service.handle(subscriptionEvent("evt_10", "customer.subscription.updated", "active",
                true, YEARLY_PRICE_ID, BASE_CREATED));

        captureOnly(SubscriptionCancelRequestedEvent.class);
    }

    @Test
    void 결제_실패로_상태만_바뀌면_주기_변경_이벤트를_내지_않는다() {
        givenMirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE, false);

        service.handle(subscriptionEvent("evt_11", "customer.subscription.updated", "past_due",
                false, YEARLY_PRICE_ID, BASE_CREATED));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void 순서가_뒤바뀐_웹훅은_아무_이벤트도_내지_않는다() {
        when(subscriptionRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(Optional.of(mirror(PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE,
                        Instant.ofEpochSecond(PERIOD_END), false, OCCURRED_AT.plusSeconds(100))));

        service.handle(subscriptionEvent("evt_12", "customer.subscription.deleted", "canceled",
                true, MONTHLY_PRICE_ID, BASE_CREATED));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // === 헬퍼 ===

    /** 발행된 이벤트가 기대 타입 하나뿐임을 확인하고 그 값을 돌려준다 — 분기는 서로 배타적이어야 한다. */
    private <T> T captureOnly(Class<T> type) {
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOf(type);
        return type.cast(published.getValue());
    }

    private void givenMirror(PlanType plan, SubscriptionStatus status, boolean cancelAtPeriodEnd) {
        when(subscriptionRepository.findByStripeSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(Optional.of(mirror(plan, status, Instant.ofEpochSecond(PERIOD_END),
                        cancelAtPeriodEnd, OCCURRED_AT.minusSeconds(100))));
    }

    private Subscription mirror(PlanType plan, SubscriptionStatus status, Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd, Instant stripeUpdatedAt) {
        return Subscription.create(MEMBER_ID, SUBSCRIPTION_ID, plan, status, currentPeriodEnd,
                cancelAtPeriodEnd, stripeUpdatedAt);
    }

    private Event subscriptionEvent(String eventId, String type, String status, boolean cancelAtPeriodEnd,
            String priceId, long created) {
        String json = """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"type":"%s",
                 "data":{"object":{"id":"%s","object":"subscription","customer":"%s","status":"%s",
                 "cancel_at_period_end":%s,
                 "items":{"object":"list","data":[{"id":"si_1","object":"subscription_item",
                 "current_period_end":%d,"price":{"id":"%s","object":"price"}}]}}}}
                """.formatted(eventId, Stripe.API_VERSION, created, type, SUBSCRIPTION_ID, CUSTOMER_ID,
                status, cancelAtPeriodEnd, PERIOD_END, priceId);
        return Webhook.constructEventWithoutVerification(json);
    }

    private static BillingProperties properties() {
        return new BillingProperties("http://localhost:3000", 30,
                Arrays.stream(BillingProduct.values()).collect(Collectors.toMap(Function.identity(),
                        product -> new BillingProperties.Product("price_" + product.name(), 100, null, true))));
    }
}
