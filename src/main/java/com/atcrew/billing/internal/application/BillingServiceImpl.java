package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.BillingService;
import com.atcrew.billing.BillingSummaryInfo;
import com.atcrew.billing.CatalogItemInfo;
import com.atcrew.billing.CheckoutSessionInfo;
import com.atcrew.billing.CompanyAccountPort;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.PortalSessionInfo;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.billing.internal.domain.BillingCustomer;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.exception.BillingErrorCode;
import com.atcrew.billing.internal.exception.BillingException;
import com.atcrew.billing.internal.infra.StripeGateway;
import com.atcrew.billing.internal.persistence.BillingCustomerRepository;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.member.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
class BillingServiceImpl implements BillingService {

    /** Customer Portal에서 돌아올 프론트 경로 — 설정 &gt; 요금제 및 결제 탭. */
    private static final String PORTAL_RETURN_PATH = "/settings/billing";

    private static final List<SubscriptionStatus> LIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptionRepository;
    private final BillingCustomerRepository customerRepository;
    private final EntitlementService entitlementService;
    private final StripeGateway stripeGateway;
    private final BillingProperties properties;
    private final MemberService memberService;
    private final CompanyAccountPort companyAccountPort;

    BillingServiceImpl(SubscriptionRepository subscriptionRepository,
            BillingCustomerRepository customerRepository,
            EntitlementService entitlementService,
            StripeGateway stripeGateway,
            BillingProperties properties,
            MemberService memberService,
            CompanyAccountPort companyAccountPort) {
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.entitlementService = entitlementService;
        this.stripeGateway = stripeGateway;
        this.properties = properties;
        this.memberService = memberService;
        this.companyAccountPort = companyAccountPort;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasProPlan(String memberId) {
        return currentSubscription(memberId).map(Subscription::grantsPro).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public int getBalance(String memberId, BillingProduct product) {
        assertOneTime(product);
        return entitlementService.getQuantity(memberId, product);
    }

    @Override
    @Transactional
    public void consume(String memberId, BillingProduct product, String refId) {
        assertOneTime(product);
        entitlementService.consume(memberId, product, refId);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingSummaryInfo getSummary(String memberId) {
        Optional<Subscription> subscription = currentSubscription(memberId);
        PlanType plan = subscription.filter(Subscription::grantsPro)
                .map(Subscription::getPlan)
                .orElse(PlanType.STARTER);
        return new BillingSummaryInfo(
                plan,
                subscription.map(Subscription::getStatus).orElse(null),
                subscription.map(Subscription::getCurrentPeriodEnd).orElse(null),
                subscription.map(Subscription::isCancelAtPeriodEnd).orElse(false),
                entitlementService.getQuantities(memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItemInfo> getCatalog(String memberId) {
        Optional<Subscription> subscription = memberId == null
                ? Optional.empty()
                : currentSubscription(memberId).filter(Subscription::grantsPro);
        boolean company = memberId != null && companyAccountPort.isCompanyAccount(memberId);
        boolean trialEligible = isTrialEligible(memberId);

        List<CatalogItemInfo> items = new ArrayList<>();
        for (BillingProduct product : BillingProduct.values()) {
            BillingProperties.Product config = properties.product(product);
            if (!config.enabled()) {
                continue; // 판매 중단 상품(PH-08) — 카탈로그에는 노출하지 않는다. 기존 보유자 게이팅은 별개.
            }
            CatalogItemInfo.CtaState cta = ctaState(product, subscription, company);
            int trialDays = cta == CatalogItemInfo.CtaState.AVAILABLE ? trialDays(product, trialEligible) : 0;
            items.add(new CatalogItemInfo(product, config.amount(), config.listAmount(),
                    BillingProperties.CURRENCY, cta, trialDays));
        }
        return items;
    }

    @Override
    @Transactional
    public CheckoutSessionInfo createCheckoutSession(String memberId, BillingProduct product) {
        if (!properties.product(product).enabled()) {
            // 카탈로그에서 숨겨도 API를 직접 호출하면 우회 구매가 가능하므로 여기서도 막는다(PH-08).
            throw new BillingException(BillingErrorCode.PRICE_NOT_CONFIGURED, "product=" + product + " disabled");
        }
        if (product.isSubscription()) {
            assertSubscribable(memberId, product);
        }
        String customerId = getOrCreateCustomer(memberId);
        if (product.isSubscription()) {
            // 체험 자격은 이 시점에 판정되므로, 앞서 열어 둔 세션이 남아 있으면 그걸로 체험을 한 번 더 받을 수 있다.
            stripeGateway.expireOpenCheckoutSessions(customerId);
        }
        return new CheckoutSessionInfo(stripeGateway.createCheckoutSession(
                memberId, product, customerId, trialDays(product, isTrialEligible(memberId))));
    }

    @Override
    @Transactional(readOnly = true)
    public PortalSessionInfo createPortalSession(String memberId) {
        BillingCustomer customer = customerRepository.findById(memberId)
                .orElseThrow(() -> new BillingException(BillingErrorCode.CUSTOMER_NOT_FOUND,
                        "memberId=" + memberId));
        return new PortalSessionInfo(
                stripeGateway.createPortalSession(customer.getStripeCustomerId(), PORTAL_RETURN_PATH));
    }

    private CatalogItemInfo.CtaState ctaState(BillingProduct product,
            Optional<Subscription> subscription, boolean company) {
        if (!product.isSubscription()) {
            return CatalogItemInfo.CtaState.AVAILABLE;
        }
        if (company) {
            return CatalogItemInfo.CtaState.UNAVAILABLE; // 프로 혜택이 창작자 기능이라 기업은 구독 대상이 아니다
        }
        if (subscription.isEmpty()) {
            return CatalogItemInfo.CtaState.AVAILABLE;
        }
        return subscription.get().getPlan() == product.toPlanType()
                ? CatalogItemInfo.CtaState.CURRENT
                : CatalogItemInfo.CtaState.CHANGE;
    }

    /** 구독 구매 가능 여부. 주기 변경(월↔연)은 Checkout이 아니라 Customer Portal에서 처리한다. */
    private void assertSubscribable(String memberId, BillingProduct product) {
        if (companyAccountPort.isCompanyAccount(memberId)) {
            throw new BillingException(BillingErrorCode.SUBSCRIPTION_NOT_ALLOWED, "memberId=" + memberId);
        }
        currentSubscription(memberId).filter(Subscription::grantsPro).ifPresent(subscription -> {
            if (subscription.getPlan() == product.toPlanType()) {
                throw new BillingException(BillingErrorCode.ALREADY_SUBSCRIBED, "memberId=" + memberId);
            }
            throw new BillingException(BillingErrorCode.SUBSCRIPTION_CHANGE_VIA_PORTAL, "memberId=" + memberId);
        });
    }

    /**
     * 무료 체험 자격. 체험 중 취소 후 재구독으로 체험을 반복하지 못하게, 취소 이력을 포함해 구독을 한 번도
     * 가진 적 없는 회원에게만 준다. 비로그인은 카탈로그 표시용으로 자격이 있다고 본다.
     */
    private boolean isTrialEligible(String memberId) {
        return memberId == null || !subscriptionRepository.existsByMemberId(memberId);
    }

    private int trialDays(BillingProduct product, boolean trialEligible) {
        return product.isSubscription() && trialEligible ? properties.trialDays() : 0;
    }

    private String getOrCreateCustomer(String memberId) {
        return customerRepository.findById(memberId)
                .map(BillingCustomer::getStripeCustomerId)
                .orElseGet(() -> {
                    String email = memberService.findById(memberId).loginEmail();
                    String stripeCustomerId = stripeGateway.createCustomer(memberId, email);
                    customerRepository.save(BillingCustomer.create(memberId, stripeCustomerId));
                    return stripeCustomerId;
                });
    }

    private Optional<Subscription> currentSubscription(String memberId) {
        return subscriptionRepository
                .findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(memberId, LIVE_STATUSES)
                .stream()
                .findFirst();
    }

    private void assertOneTime(BillingProduct product) {
        if (product.isSubscription()) {
            throw new BillingException(BillingErrorCode.INVALID_PRODUCT, "product=" + product);
        }
    }
}
