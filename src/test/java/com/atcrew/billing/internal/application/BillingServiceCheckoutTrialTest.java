package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.CheckoutSessionInfo;
import com.atcrew.billing.CompanyAccountPort;
import com.atcrew.billing.SubscriptionCheckoutStartedEvent;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.billing.internal.domain.BillingCustomer;
import com.atcrew.billing.internal.infra.CheckoutSession;
import com.atcrew.billing.internal.infra.StripeGateway;
import com.atcrew.billing.internal.persistence.BillingCustomerRepository;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checkout 생성 경로가 실제로 Stripe에 넘기는 체험 일수를 검증한다. 카탈로그 표시값({@code BillingModuleTests})과
 * 달리 이쪽이 돈이 오가는 경로라, 판정 결과가 gateway 인자까지 이어지는지 따로 고정한다.
 */
class BillingServiceCheckoutTrialTest {

    private static final String MEMBER_ID = "member-1";
    private static final String CUSTOMER_ID = "cus_1";
    private static final String SESSION_ID = "cs_1";

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final BillingCustomerRepository customerRepository = mock(BillingCustomerRepository.class);
    private final StripeGateway stripeGateway = mock(StripeGateway.class);
    private final CompanyAccountPort companyAccountPort = mock(CompanyAccountPort.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final BillingServiceImpl service = new BillingServiceImpl(subscriptionRepository, customerRepository,
            mock(EntitlementService.class), stripeGateway, properties(), mock(MemberService.class),
            companyAccountPort, eventPublisher);

    @Test
    void 구독_이력이_없으면_열린_세션을_닫고_30일_체험으로_세션을_만든다() {
        givenCustomer();
        when(subscriptionRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        service.createCheckoutSession(MEMBER_ID, BillingProduct.PRO_YEARLY);

        InOrder order = inOrder(stripeGateway);
        order.verify(stripeGateway).expireOpenCheckoutSessions(CUSTOMER_ID);
        order.verify(stripeGateway).createCheckoutSession(MEMBER_ID, BillingProduct.PRO_YEARLY, CUSTOMER_ID, 30);
    }

    @Test
    void 구독_이력이_있으면_체험_없이_세션을_만든다() {
        givenCustomer();
        when(subscriptionRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        service.createCheckoutSession(MEMBER_ID, BillingProduct.PRO_MONTHLY);

        verify(stripeGateway).createCheckoutSession(MEMBER_ID, BillingProduct.PRO_MONTHLY, CUSTOMER_ID, 0);
    }

    @Test
    void 세션_생성에_성공하면_세션_ID와_체험_일수를_실은_시작_이벤트를_발행한다() {
        givenCustomer();
        when(subscriptionRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        CheckoutSessionInfo info = service.createCheckoutSession(MEMBER_ID, BillingProduct.PRO_MONTHLY);

        assertThat(info.checkoutUrl()).isEqualTo("https://checkout.stripe.test/" + SESSION_ID);
        ArgumentCaptor<SubscriptionCheckoutStartedEvent> published =
                ArgumentCaptor.forClass(SubscriptionCheckoutStartedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(published.getValue().checkoutSessionId()).isEqualTo(SESSION_ID);
        assertThat(published.getValue().product()).isEqualTo(BillingProduct.PRO_MONTHLY);
        assertThat(published.getValue().trialDays()).isEqualTo(30);
        assertThat(published.getValue().startedAt()).isNotNull();
    }

    @Test
    void 단건_결제는_열린_세션을_닫지_않고_체험도_붙이지_않는다() {
        givenCustomer();

        service.createCheckoutSession(MEMBER_ID, BillingProduct.BOOST);

        verify(stripeGateway, never()).expireOpenCheckoutSessions(anyString());
        verify(stripeGateway).createCheckoutSession(MEMBER_ID, BillingProduct.BOOST, CUSTOMER_ID, 0);
        // 단건 상품은 구독 전환 퍼널이 아니다 — subscription_checkout_started를 내면 안 된다.
        verify(eventPublisher, never()).publishEvent(any(SubscriptionCheckoutStartedEvent.class));
    }

    private void givenCustomer() {
        when(companyAccountPort.isCompanyAccount(MEMBER_ID)).thenReturn(false);
        when(subscriptionRepository.findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(
                eq(MEMBER_ID), anyCollection()))
                .thenReturn(List.of());
        when(customerRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(BillingCustomer.create(MEMBER_ID, CUSTOMER_ID)));
        when(stripeGateway.createCheckoutSession(eq(MEMBER_ID), any(BillingProduct.class), eq(CUSTOMER_ID), anyInt()))
                .thenReturn(new CheckoutSession(SESSION_ID, "https://checkout.stripe.test/" + SESSION_ID));
    }

    private static BillingProperties properties() {
        return new BillingProperties("http://localhost:3000", 30,
                Arrays.stream(BillingProduct.values()).collect(Collectors.toMap(Function.identity(),
                        product -> new BillingProperties.Product("price_" + product.name(), 100, null, true))));
    }
}
