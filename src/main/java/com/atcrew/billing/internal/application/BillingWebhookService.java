package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PaymentMethodUpdatedEvent;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionActivatedEvent;
import com.atcrew.billing.SubscriptionBillingIntervalChangedEvent;
import com.atcrew.billing.SubscriptionCancelRequestRevokedEvent;
import com.atcrew.billing.SubscriptionCancelRequestedEvent;
import com.atcrew.billing.SubscriptionEndReason;
import com.atcrew.billing.SubscriptionEndedEvent;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import com.atcrew.billing.SubscriptionPaymentFailureRecordedEvent;
import com.atcrew.billing.SubscriptionPaymentRecoveredEvent;
import com.atcrew.billing.SubscriptionPaymentSucceededEvent;
import com.atcrew.billing.SubscriptionPaymentType;
import com.atcrew.billing.SubscriptionReactivatedEvent;
import com.atcrew.billing.SubscriptionRefundedEvent;
import com.atcrew.billing.SubscriptionRenewedEvent;
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
import com.stripe.model.PaymentMethod;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

    private static final String SUBSCRIPTION_DELETED = "customer.subscription.deleted";

    /** 구독을 처음 시작할 때 발행되는 invoice의 billing_reason. */
    private static final String BILLING_REASON_SUBSCRIPTION_CREATE = "subscription_create";

    /** 결제 주기가 돌아와 발행되는 invoice의 billing_reason — 무료 체험 종료 후 첫 청구도 이 값이다. */
    private static final String BILLING_REASON_SUBSCRIPTION_CYCLE = "subscription_cycle";

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
            case "payment_method.attached" -> onPaymentMethodUpdated(event, occurredAt);
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

        boolean deleted = SUBSCRIPTION_DELETED.equals(event.getType());
        SubscriptionStatus status = deleted
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

        Optional<Subscription> mirrored =
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.getId());
        if (mirrored.isEmpty()) {
            // 재구독 판정에 쓸 직전 구독은 새 행을 저장하기 전에 찾는다 — 저장 뒤에는 방금 만든 행이 섞인다.
            Optional<Subscription> previouslyCanceled = !deleted && status == SubscriptionStatus.ACTIVE
                    ? subscriptionRepository.findFirstByMemberIdAndStatusOrderByStripeUpdatedAtDesc(
                            memberId.get(), SubscriptionStatus.CANCELED)
                    : Optional.empty();
            Subscription created = subscriptionRepository.save(Subscription.create(memberId.get(),
                    stripeSubscription.getId(), plan, status, periodEnd, cancelAtPeriodEnd, occurredAt));
            publishCreatedAnalytics(event, created, previouslyCanceled, deleted, occurredAt);
            return;
        }

        Subscription existing = mirrored.get();
        // sync()가 필드를 덮어쓰기 전의 값 — 상태 전이 판정은 이 세 값과 반영 후 값을 비교해서 한다.
        SubscriptionStatus previousStatus = existing.getStatus();
        PlanType previousPlan = existing.getPlan();
        boolean previousCancelAtPeriodEnd = existing.isCancelAtPeriodEnd();
        if (!existing.sync(plan, status, periodEnd, cancelAtPeriodEnd, occurredAt)) {
            log.debug("이전 시각의 구독 이벤트 무시 eventId={}, subscriptionId={}",
                    event.getId(), stripeSubscription.getId());
            return;
        }
        publishUpdatedAnalytics(event, existing, previousStatus, previousPlan, previousCancelAtPeriodEnd,
                deleted, occurredAt);
    }

    /**
     * 미러에 없던 구독이 새로 생긴 경우의 분석 이벤트. 최초 활성화·재활성화·종료 중 하나만 발행한다
     * (PA-08) — 같은 Stripe 이벤트에서 둘 이상 나오면 집계가 중복된다.
     */
    private void publishCreatedAnalytics(Event event, Subscription created,
            Optional<Subscription> previouslyCanceled, boolean deleted, Instant occurredAt) {
        if (deleted) {
            // 미러에 없던 구독의 삭제 — 직전 상태를 모르니 사유를 판정하지 않는다.
            eventPublisher.publishEvent(new SubscriptionEndedEvent(created.getMemberId(),
                    created.getStripeSubscriptionId(), created.getPlan(), SubscriptionEndReason.OTHER,
                    occurredAt, event.getId()));
            return;
        }
        if (created.getStatus() != SubscriptionStatus.ACTIVE) {
            return; // 결제 미완료로 들어온 구독 — 혜택이 열리지 않았으므로 활성화로 세지 않는다
        }
        if (previouslyCanceled.isPresent()) {
            eventPublisher.publishEvent(new SubscriptionReactivatedEvent(created.getMemberId(),
                    created.getStripeSubscriptionId(), created.getPlan(),
                    daysSinceEnd(previouslyCanceled.get(), occurredAt), occurredAt, event.getId()));
            return;
        }
        eventPublisher.publishEvent(new SubscriptionActivatedEvent(created.getMemberId(),
                created.getStripeSubscriptionId(), created.getPlan(), occurredAt, event.getId()));
    }

    /**
     * 미러에 있던 구독이 갱신된 경우의 분석 이벤트. 종료 &gt; 취소 예약 변화 &gt; 결제 주기 변화 순으로
     * 하나만 발행한다(PA-08). 결제 실패·복구로 인한 상태 전이는 invoice 계열이 담당한다.
     */
    private void publishUpdatedAnalytics(Event event, Subscription subscription,
            SubscriptionStatus previousStatus, PlanType previousPlan, boolean previousCancelAtPeriodEnd,
            boolean deleted, Instant occurredAt) {
        if (deleted) {
            eventPublisher.publishEvent(new SubscriptionEndedEvent(subscription.getMemberId(),
                    subscription.getStripeSubscriptionId(), subscription.getPlan(),
                    endReason(previousStatus, previousCancelAtPeriodEnd), occurredAt, event.getId()));
            return;
        }
        if (previousCancelAtPeriodEnd != subscription.isCancelAtPeriodEnd()) {
            if (subscription.isCancelAtPeriodEnd()) {
                eventPublisher.publishEvent(new SubscriptionCancelRequestedEvent(subscription.getMemberId(),
                        subscription.getStripeSubscriptionId(), subscription.getPlan(),
                        subscription.getCurrentPeriodEnd(), occurredAt, event.getId()));
            } else {
                eventPublisher.publishEvent(new SubscriptionCancelRequestRevokedEvent(
                        subscription.getMemberId(), subscription.getStripeSubscriptionId(),
                        subscription.getPlan(), subscription.getCurrentPeriodEnd(), occurredAt,
                        event.getId()));
            }
            return;
        }
        if (previousPlan != subscription.getPlan() && previousStatus == subscription.getStatus()) {
            eventPublisher.publishEvent(new SubscriptionBillingIntervalChangedEvent(
                    subscription.getMemberId(), subscription.getStripeSubscriptionId(), previousPlan,
                    subscription.getPlan(), occurredAt, event.getId()));
        }
    }

    /** 종료 사유는 삭제 웹훅이 오기 직전까지 미러에 남아 있던 상태로 판정한다. */
    private SubscriptionEndReason endReason(SubscriptionStatus previousStatus,
            boolean previousCancelAtPeriodEnd) {
        if (previousCancelAtPeriodEnd) {
            return SubscriptionEndReason.VOLUNTARY;
        }
        if (previousStatus == SubscriptionStatus.PAST_DUE) {
            return SubscriptionEndReason.PAYMENT_FAILED;
        }
        return SubscriptionEndReason.OTHER;
    }

    /**
     * 직전 구독이 끝난 뒤 지난 일수. 즉시 취소는 주기 종료 시각이 미래로 남아 음수가 될 수 있어 0으로 묶는다.
     */
    private Long daysSinceEnd(Subscription previous, Instant occurredAt) {
        Instant previousPeriodEnd = previous.getCurrentPeriodEnd();
        if (previousPeriodEnd == null) {
            return null;
        }
        return Math.max(0L, Duration.between(previousPeriodEnd, occurredAt).toDays());
    }

    /**
     * 정기 결제 실패 — 유예 없이 즉시 스타터로 내리고 알림 이벤트를 발행한다(설정-R03).
     * 알림용과 별개로 집계용 이벤트도 같이 낸다(PA-09) — 수신 모듈이 달라 계약을 섞지 않는다.
     */
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
                        eventPublisher.publishEvent(new SubscriptionPaymentFailureRecordedEvent(
                                subscription.getMemberId(), subscription.getStripeSubscriptionId(),
                                subscription.getPlan(), paymentType(invoice), failureCode(invoice),
                                invoice.getAmountDue(), currency(invoice), occurredAt, event.getId()));
                    }
                });
    }

    /**
     * 결제 성공 — 복구 대기였다면 웹훅 기준으로 플랜을 복원한다(설정-R03). 집계용으로는 복구/최초/갱신
     * 중 하나만 발행한다(PA-09) — 한 웹훅에서 둘 이상 나오면 결제 건수가 중복으로 세어진다.
     *
     * <p>카운터(renewal_number·recovery_attempt_number) 증가는 별도 save 없이 dirty checking으로
     * 반영된다 — 이 메서드는 {@link #handle} 트랜잭션 안에서 영속 상태의 엔티티를 바꾼다.
     */
    private void onPaymentSucceeded(Event event, Instant occurredAt) {
        Invoice invoice = dataObject(event, Invoice.class);
        Optional<Subscription> mirrored = resolveMemberId(invoice.getCustomer(), null)
                .flatMap(this::currentSubscription);
        if (mirrored.isEmpty()) {
            log.debug("미러에 없는 구독의 결제 성공 무시 eventId={}, customerId={}",
                    event.getId(), invoice.getCustomer());
            return;
        }

        Subscription subscription = mirrored.get();
        // sync()가 상태를 덮어쓰기 전의 값 — 복구인지 정상 결제인지는 이 값으로만 가릴 수 있다.
        SubscriptionStatus previousStatus = subscription.getStatus();
        if (previousStatus == SubscriptionStatus.PAST_DUE) {
            if (!subscription.sync(subscription.getPlan(), SubscriptionStatus.ACTIVE,
                    subscription.getCurrentPeriodEnd(), subscription.isCancelAtPeriodEnd(), occurredAt)) {
                log.debug("이전 시각의 결제 성공 이벤트 무시 eventId={}, subscriptionId={}",
                        event.getId(), subscription.getStripeSubscriptionId());
                return;
            }
            int recoveryAttemptNumber = subscription.incrementRecoveryAttemptNumber();
            eventPublisher.publishEvent(new SubscriptionPaymentRecoveredEvent(subscription.getMemberId(),
                    subscription.getStripeSubscriptionId(), subscription.getPlan(),
                    recoveryAttemptNumber, invoice.getAmountPaid(), currency(invoice), occurredAt,
                    event.getId()));
            return;
        }

        SubscriptionPaymentType paymentType = paymentType(invoice);
        if (paymentType == SubscriptionPaymentType.INITIAL) {
            eventPublisher.publishEvent(new SubscriptionPaymentSucceededEvent(subscription.getMemberId(),
                    subscription.getStripeSubscriptionId(), subscription.getPlan(), paymentType,
                    invoice.getAmountPaid(), currency(invoice), occurredAt, event.getId()));
            return;
        }
        if (paymentType == SubscriptionPaymentType.RENEWAL) {
            int renewalNumber = subscription.incrementRenewalNumber();
            eventPublisher.publishEvent(new SubscriptionRenewedEvent(subscription.getMemberId(),
                    subscription.getStripeSubscriptionId(), subscription.getPlan(), renewalNumber,
                    invoice.getAmountPaid(), currency(invoice), occurredAt, event.getId()));
            return;
        }
        // 플랜 변경 비례 배분(subscription_update)·수동 청구(manual) 등 — 구독 결제 건수로 세지 않는다.
        // 특히 월↔연 전환은 PA-08의 subscription_billing_interval_changed로 이미 집계되므로,
        // 여기서 갱신으로 세면 같은 사건이 두 번 잡힌다.
        log.debug("구독 결제 유형을 판별하지 못해 집계하지 않습니다 eventId={}, billingReason={}",
                event.getId(), invoice.getBillingReason());
    }

    /**
     * 결제 수단 등록 — 카드 정보 없이 사건과 그 시점의 구독 상태만 집계한다(PA-09).
     *
     * <p>기본 결제 수단 변경({@code customer.updated})은 구독하지 않는다. 변경 여부를 알려면 Stripe가
     * 주는 {@code previous_attributes}에 의존해야 하는데, PA-08에서 그 방식을 미러 스냅샷 비교로 대체했고
     * Customer의 기본 결제 수단은 미러에 없어 비교 대상이 없다. 게다가 이 웹훅은 주소·이메일 변경 등
     * 결제와 무관한 수정에도 오므로, 그대로 받으면 사건이 아닌 것까지 결제 수단 변경으로 집계된다.
     */
    private void onPaymentMethodUpdated(Event event, Instant occurredAt) {
        PaymentMethod paymentMethod = dataObject(event, PaymentMethod.class);
        Optional<String> memberId = resolveMemberId(paymentMethod.getCustomer(), null);
        if (memberId.isEmpty()) {
            log.debug("회원을 찾을 수 없는 결제 수단 이벤트 무시 eventId={}, customerId={}",
                    event.getId(), paymentMethod.getCustomer());
            return;
        }
        SubscriptionStatus status = currentSubscription(memberId.get())
                .map(Subscription::getStatus)
                .orElse(null);
        eventPublisher.publishEvent(
                new PaymentMethodUpdatedEvent(memberId.get(), status, occurredAt, event.getId()));
    }

    /** invoice의 billing_reason으로 결제 유형을 가린다. 아는 값이 아니면 null(집계 대상 아님). */
    private SubscriptionPaymentType paymentType(Invoice invoice) {
        String billingReason = invoice.getBillingReason();
        if (BILLING_REASON_SUBSCRIPTION_CREATE.equals(billingReason)) {
            return SubscriptionPaymentType.INITIAL;
        }
        if (BILLING_REASON_SUBSCRIPTION_CYCLE.equals(billingReason)) {
            return SubscriptionPaymentType.RENEWAL;
        }
        return null;
    }

    /**
     * 결제 실패 코드. 카드 거절 사유(decline_code)가 있으면 그것을, 없으면 Stripe 에러 코드를 쓴다.
     *
     * <p>대부분의 경우 null이다 — 지금 SDK(stripe-java 33.x)의 Invoice에는 결제 거절 정보를 담던
     * {@code last_payment_error}가 없고 {@code last_finalization_error}(청구서 확정 실패)만 남아 있다.
     * 거절 사유의 정본은 PaymentIntent인데, 그걸 읽으려면 billing에 지금 없는 Stripe 능동 호출이 필요해
     * PA-09 범위를 벗어난다. 코드 없이도 실패 건수·유형 집계는 성립하므로 여기서는 받아올 수 있는 값만 싣는다.
     */
    private String failureCode(Invoice invoice) {
        StripeError error = invoice.getLastFinalizationError();
        if (error == null) {
            return null;
        }
        return error.getDeclineCode() != null ? error.getDeclineCode() : error.getCode();
    }

    /** 통화는 USD 단일이지만 invoice가 준 값을 쓴다. Stripe는 소문자로 주므로 모듈 표기(대문자)로 맞춘다. */
    private String currency(Invoice invoice) {
        return invoice.getCurrency() == null
                ? null
                : invoice.getCurrency().toUpperCase(Locale.ROOT);
    }

    /**
     * 환불 — 지급했던 단건 권한을 회수한다(D12). 부분 환불은 지급 단위와 대응하지 않으므로 회수하지 않는다.
     */
    private void onChargeRefunded(Event event) {
        Charge charge = dataObject(event, Charge.class);
        boolean fullyRefunded = Boolean.TRUE.equals(charge.getRefunded());
        // 권한 회수와 무관하게 환불 자체는 부분이어도 분석 이벤트로 남긴다.
        resolveMemberId(charge.getCustomer(), null).ifPresent(memberId ->
                eventPublisher.publishEvent(new SubscriptionRefundedEvent(memberId,
                        fullyRefunded ? "full" : "partial",
                        charge.getAmountRefunded() == null ? 0L : charge.getAmountRefunded(),
                        charge.getCurrency() == null ? null : charge.getCurrency().toUpperCase(),
                        event.getId())));
        if (!fullyRefunded) {
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
