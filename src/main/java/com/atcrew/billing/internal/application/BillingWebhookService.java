package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.billing.internal.domain.BillingCustomer;
import com.atcrew.billing.internal.domain.EntitlementLedger;
import com.atcrew.billing.internal.domain.LedgerReason;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.domain.WebhookEvent;
import com.atcrew.billing.internal.persistence.BillingCustomerRepository;
import com.atcrew.billing.internal.persistence.EntitlementLedgerRepository;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.billing.internal.persistence.WebhookEventRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stripe 웹훅 처리. 웹훅이 결제 결과의 단일 진실 소스이며, Checkout 복귀 URL에서는 상태를 바꾸지 않는다(D16).
 *
 * <p>같은 이벤트가 재전송돼도 1회만 반영되도록 event id를 선삽입해 멱등을 보장하고,
 * 처리 중 예외가 나면 트랜잭션이 통째로 롤백돼 Stripe 재시도로 복구된다.
 */
@Service
public class BillingWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookService.class);

    private static final List<SubscriptionStatus> LIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingCustomerRepository customerRepository;
    private final EntitlementLedgerRepository ledgerRepository;
    private final EntitlementService entitlementService;
    private final BillingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final BillingMetrics metrics;

    BillingWebhookService(WebhookEventRepository webhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            BillingCustomerRepository customerRepository,
            EntitlementLedgerRepository ledgerRepository,
            EntitlementService entitlementService,
            BillingProperties properties,
            ApplicationEventPublisher eventPublisher,
            BillingMetrics metrics) {
        this.webhookEventRepository = webhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.entitlementService = entitlementService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Transactional
    public void handle(Event event) {
        if (webhookEventRepository.existsById(event.getId())) {
            log.debug("이미 처리한 웹훅 이벤트 무시 eventId={}, type={}", event.getId(), event.getType());
            return;
        }
        WebhookEvent webhookEvent = webhookEventRepository.save(
                WebhookEvent.received(event.getId(), event.getType()));

        Instant occurredAt = Instant.ofEpochSecond(event.getCreated());
        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted(event);
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> onSubscriptionChanged(event, occurredAt);
            case "invoice.payment_failed" -> onPaymentFailed(event, occurredAt);
            case "invoice.payment_succeeded" -> onPaymentSucceeded(event, occurredAt);
            case "charge.refunded" -> onChargeRefunded(event);
            default -> log.debug("구독하지 않는 이벤트 무시 type={}", event.getType());
        }

        webhookEvent.markProcessed(Instant.now());
    }

    /** 단건 결제 완료 → 보유 개수 지급. 구독 결제는 customer.subscription.* 에서 처리한다. */
    private void onCheckoutCompleted(Event event) {
        com.stripe.model.checkout.Session session =
                dataObject(event, com.stripe.model.checkout.Session.class);
        if (!"payment".equals(session.getMode())) {
            return;
        }

        String memberId = session.getMetadata() == null ? null : session.getMetadata().get("memberId");
        String productName = session.getMetadata() == null ? null : session.getMetadata().get("product");
        if (memberId == null || productName == null) {
            log.warn("메타데이터가 없는 결제 세션 무시 eventId={}, sessionId={}", event.getId(), session.getId());
            return;
        }

        BillingProduct product = BillingProduct.valueOf(productName);
        String paymentIntentId = session.getPaymentIntent();
        entitlementService.grant(memberId, product, event.getId(), paymentIntentId);

        // 팀원 모집글 업로드 권한에는 끌어올리기 1회가 포함된다(요금제-R06).
        if (product == BillingProduct.TEAM_POSTING) {
            entitlementService.grant(memberId, BillingProduct.BOOST, event.getId(), paymentIntentId);
        }

        metrics.checkoutCompleted(product);
    }

    private void onSubscriptionChanged(Event event, Instant occurredAt) {
        com.stripe.model.Subscription stripeSubscription =
                dataObject(event, com.stripe.model.Subscription.class);

        Optional<String> memberId = resolveMemberId(stripeSubscription.getCustomer(),
                stripeSubscription.getMetadata() == null
                        ? null
                        : stripeSubscription.getMetadata().get("memberId"));
        if (memberId.isEmpty()) {
            log.warn("회원을 찾을 수 없는 구독 이벤트 무시 eventId={}, customerId={}",
                    event.getId(), stripeSubscription.getCustomer());
            return;
        }

        SubscriptionStatus status = "customer.subscription.deleted".equals(event.getType())
                ? SubscriptionStatus.CANCELED
                : toStatus(stripeSubscription.getStatus());
        PlanType plan = resolvePlan(stripeSubscription).orElseGet(() -> {
            // Price ID 설정이 비어 있거나 대시보드에서 상품을 바꾼 경우 — 월간으로 취급하되 흔적을 남긴다.
            log.warn("구독의 Price ID로 플랜을 판별하지 못해 PRO_MONTHLY로 처리합니다 subscriptionId={}",
                    stripeSubscription.getId());
            return PlanType.PRO_MONTHLY;
        });
        Instant periodEnd = resolvePeriodEnd(stripeSubscription);
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd());

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId())
                .ifPresentOrElse(
                        existing -> {
                            if (!existing.sync(plan, status, periodEnd, cancelAtPeriodEnd, occurredAt)) {
                                log.debug("이전 시각의 구독 이벤트 무시 eventId={}, subscriptionId={}",
                                        event.getId(), stripeSubscription.getId());
                            }
                        },
                        () -> subscriptionRepository.save(Subscription.create(memberId.get(),
                                stripeSubscription.getId(), plan, status, periodEnd,
                                cancelAtPeriodEnd, occurredAt)));
    }

    /** 정기 결제 실패 — 유예 없이 즉시 스타터로 내리고 알림 이벤트를 발행한다(설정-R03). */
    private void onPaymentFailed(Event event, Instant occurredAt) {
        Invoice invoice = dataObject(event, Invoice.class);
        resolveMemberId(invoice.getCustomer(), null)
                .flatMap(this::currentSubscription)
                .ifPresent(subscription -> {
                    boolean changed = subscription.sync(subscription.getPlan(), SubscriptionStatus.PAST_DUE,
                            subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(), occurredAt);
                    if (changed) {
                        eventPublisher.publishEvent(new SubscriptionPaymentFailedEvent(
                                subscription.getMemberId(), subscription.getPlan(), occurredAt));
                    }
                });
    }

    /** 재결제 성공 — 웹훅 기준으로 플랜을 복원한다(설정-R03). */
    private void onPaymentSucceeded(Event event, Instant occurredAt) {
        Invoice invoice = dataObject(event, Invoice.class);
        resolveMemberId(invoice.getCustomer(), null)
                .flatMap(this::currentSubscription)
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.PAST_DUE)
                .ifPresent(subscription -> subscription.sync(subscription.getPlan(),
                        SubscriptionStatus.ACTIVE, subscription.getCurrentPeriodEnd(),
                        subscription.isCancelAtPeriodEnd(), occurredAt));
    }

    /**
     * 환불 — 지급했던 단건 권한을 회수한다(D12). 부분 환불은 지급 단위와 대응하지 않으므로 회수하지 않는다.
     */
    private void onChargeRefunded(Event event) {
        Charge charge = dataObject(event, Charge.class);
        if (!Boolean.TRUE.equals(charge.getRefunded())) {
            log.info("부분 환불은 권한을 회수하지 않습니다 chargeId={}", charge.getId());
            return;
        }
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }
        List<EntitlementLedger> granted =
                ledgerRepository.findByRefIdAndReason(paymentIntentId, LedgerReason.PURCHASE);
        for (EntitlementLedger ledger : granted) {
            entitlementService.revoke(ledger.getMemberId(), ledger.getProduct(),
                    event.getId(), paymentIntentId);
        }
    }

    private Optional<Subscription> currentSubscription(String memberId) {
        return subscriptionRepository
                .findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(memberId, LIVE_STATUSES)
                .stream()
                .findFirst();
    }

    /** Stripe Customer ID로 회원을 찾고, 매핑이 없으면 메타데이터에 실어 보낸 회원 ID를 쓴다. */
    private Optional<String> resolveMemberId(String stripeCustomerId, String metadataMemberId) {
        if (stripeCustomerId != null) {
            Optional<String> mapped = customerRepository.findByStripeCustomerId(stripeCustomerId)
                    .map(BillingCustomer::getMemberId);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return Optional.ofNullable(metadataMemberId);
    }

    /** 구독 아이템의 Price ID로 플랜을 판별한다. */
    private Optional<PlanType> resolvePlan(com.stripe.model.Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData() == null) {
            return Optional.empty();
        }
        for (SubscriptionItem item : subscription.getItems().getData()) {
            if (item.getPrice() == null) {
                continue;
            }
            for (BillingProduct product : BillingProduct.values()) {
                if (!product.isSubscription()) {
                    continue;
                }
                String configuredPriceId = properties.product(product).priceId();
                if (configuredPriceId != null && configuredPriceId.equals(item.getPrice().getId())) {
                    return Optional.of(product.toPlanType());
                }
            }
        }
        return Optional.empty();
    }

    /** 결제 주기 종료 시각은 구독이 아니라 구독 아이템에 있다(최신 Stripe API). */
    private Instant resolvePeriodEnd(com.stripe.model.Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData() == null) {
            return null;
        }
        return subscription.getItems().getData().stream()
                .map(SubscriptionItem::getCurrentPeriodEnd)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Instant::ofEpochSecond)
                .orElse(null);
    }

    private SubscriptionStatus toStatus(String stripeStatus) {
        return switch (stripeStatus == null ? "" : stripeStatus) {
            case "active", "trialing" -> SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid", "incomplete" -> SubscriptionStatus.PAST_DUE;
            default -> SubscriptionStatus.CANCELED;
        };
    }

    /**
     * 이벤트 페이로드를 모델 객체로 변환한다. 계정의 Stripe API 버전이 SDK와 다르면 표준 역직렬화가
     * 비어서 오므로, 그때는 알려진 필드만 매핑하는 unsafe 경로로 넘어간다.
     */
    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T dataObject(Event event, Class<T> type) {
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (object == null) {
            try {
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("웹훅 페이로드를 해석하지 못했습니다: " + event.getType(), e);
            }
        }
        if (!type.isInstance(object)) {
            throw new IllegalStateException(
                    "웹훅 페이로드 타입이 예상과 다릅니다: " + object.getClass().getSimpleName());
        }
        return (T) object;
    }
}
