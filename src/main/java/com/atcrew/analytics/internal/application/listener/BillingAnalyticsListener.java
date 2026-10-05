package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.billing.PaymentMethodUpdatedEvent;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionActivatedEvent;
import com.atcrew.billing.SubscriptionBillingIntervalChangedEvent;
import com.atcrew.billing.SubscriptionCancelRequestRevokedEvent;
import com.atcrew.billing.SubscriptionCancelRequestedEvent;
import com.atcrew.billing.SubscriptionCheckoutStartedEvent;
import com.atcrew.billing.SubscriptionEndedEvent;
import com.atcrew.billing.SubscriptionPaymentFailureRecordedEvent;
import com.atcrew.billing.SubscriptionPaymentRecoveredEvent;
import com.atcrew.billing.SubscriptionPaymentSucceededEvent;
import com.atcrew.billing.SubscriptionPaymentType;
import com.atcrew.billing.SubscriptionReactivatedEvent;
import com.atcrew.billing.SubscriptionRenewedEvent;
import com.atcrew.billing.SubscriptionStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * billing 모듈 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events PA-08).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고. 여기 이벤트는 모두 성공 계열이라
 * 전부 {@code @ApplicationModuleListener}를 쓴다.
 *
 * <p>구독 상태 전이 이벤트의 {@code $insert_id}는 Stripe 이벤트 ID로 만든다. 웹훅 하나에서 나오는 분기
 * 결과가 하나뿐이라 이벤트명 + Stripe 이벤트 ID로 사건이 특정된다.
 */
@Component
class BillingAnalyticsListener {

    private static final String CHECKOUT_STARTED = "subscription_checkout_started";
    private static final String ACTIVATED = "subscription_activated";
    private static final String REACTIVATED = "subscription_reactivated";
    private static final String CANCEL_REQUESTED = "subscription_cancel_requested";
    private static final String CANCEL_REQUEST_REVOKED = "subscription_cancel_request_revoked";
    private static final String ENDED = "subscription_ended";
    private static final String BILLING_INTERVAL_CHANGED = "subscription_billing_interval_changed";
    private static final String PAYMENT_SUCCEEDED = "subscription_payment_succeeded";
    private static final String RENEWED = "subscription_renewed";
    private static final String PAYMENT_FAILED = "subscription_payment_failed";
    private static final String PAYMENT_RECOVERED = "subscription_payment_recovered";
    private static final String PAYMENT_METHOD_UPDATED = "payment_method_updated";

    private final AnalyticsEventPublisher analyticsEventPublisher;

    BillingAnalyticsListener(AnalyticsEventPublisher analyticsEventPublisher) {
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    /** 결제창 도달 — 세션 ID로 사건이 특정되므로 $insert_id도 그것으로 만든다. */
    @ApplicationModuleListener
    void onCheckoutStarted(SubscriptionCheckoutStartedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("checkout_session_id", event.checkoutSessionId());
        properties.put("product", event.product().name());
        properties.put("trial_days", event.trialDays());
        properties.put("started_at", iso(event.startedAt()));
        analyticsEventPublisher.publish(CHECKOUT_STARTED, event.memberId(), properties,
                AnalyticsInsertId.of(CHECKOUT_STARTED, event.checkoutSessionId()));
    }

    /** 최초 구독 활성화. 과거 취소 이력이 있는 재구독은 {@link #onReactivated}가 담당한다. */
    @ApplicationModuleListener
    void onActivated(SubscriptionActivatedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("activated_at", iso(event.activatedAt()));
        analyticsEventPublisher.publish(ACTIVATED, event.memberId(), properties,
                AnalyticsInsertId.of(ACTIVATED, event.stripeEventId()));
    }

    /** 재구독. days_since_end는 직전 구독 주기 종료 이후 지난 일수이며, 종료 시각을 모르면 빠진다. */
    @ApplicationModuleListener
    void onReactivated(SubscriptionReactivatedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("days_since_end", event.daysSinceEnd());
        properties.put("reactivated_at", iso(event.reactivatedAt()));
        analyticsEventPublisher.publish(REACTIVATED, event.memberId(), properties,
                AnalyticsInsertId.of(REACTIVATED, event.stripeEventId()));
    }

    @ApplicationModuleListener
    void onCancelRequested(SubscriptionCancelRequestedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("current_period_end", iso(event.currentPeriodEnd()));
        properties.put("requested_at", iso(event.requestedAt()));
        analyticsEventPublisher.publish(CANCEL_REQUESTED, event.memberId(), properties,
                AnalyticsInsertId.of(CANCEL_REQUESTED, event.stripeEventId()));
    }

    @ApplicationModuleListener
    void onCancelRequestRevoked(SubscriptionCancelRequestRevokedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("current_period_end", iso(event.currentPeriodEnd()));
        properties.put("revoked_at", iso(event.revokedAt()));
        analyticsEventPublisher.publish(CANCEL_REQUEST_REVOKED, event.memberId(), properties,
                AnalyticsInsertId.of(CANCEL_REQUEST_REVOKED, event.stripeEventId()));
    }

    @ApplicationModuleListener
    void onEnded(SubscriptionEndedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("end_reason", event.endReason().name().toLowerCase(Locale.ROOT));
        properties.put("ended_at", iso(event.endedAt()));
        analyticsEventPublisher.publish(ENDED, event.memberId(), properties,
                AnalyticsInsertId.of(ENDED, event.stripeEventId()));
    }

    @ApplicationModuleListener
    void onBillingIntervalChanged(SubscriptionBillingIntervalChangedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("previous_interval", billingInterval(event.previousPlan()));
        properties.put("new_interval", billingInterval(event.newPlan()));
        properties.put("effective_at", iso(event.effectiveAt()));
        analyticsEventPublisher.publish(BILLING_INTERVAL_CHANGED, event.memberId(), properties,
                AnalyticsInsertId.of(BILLING_INTERVAL_CHANGED, event.stripeEventId()));
    }

    /** 구독 최초 결제 성공. 정기 갱신은 {@link #onRenewed}가 담당한다. */
    @ApplicationModuleListener
    void onPaymentSucceeded(SubscriptionPaymentSucceededEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("payment_type", paymentType(event.paymentType()));
        properties.put("amount", event.amount());
        properties.put("currency", event.currency());
        properties.put("paid_at", iso(event.paidAt()));
        analyticsEventPublisher.publish(PAYMENT_SUCCEEDED, event.memberId(), properties,
                AnalyticsInsertId.of(PAYMENT_SUCCEEDED, event.stripeEventId()));
    }

    /** 정기 갱신 결제 성공. renewal_number는 카운터 도입(V49) 이후 건만 센 값이다. */
    @ApplicationModuleListener
    void onRenewed(SubscriptionRenewedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("renewal_number", event.renewalNumber());
        properties.put("amount", event.amount());
        properties.put("currency", event.currency());
        properties.put("renewed_at", iso(event.renewedAt()));
        analyticsEventPublisher.publish(RENEWED, event.memberId(), properties,
                AnalyticsInsertId.of(RENEWED, event.stripeEventId()));
    }

    /**
     * 구독 결제 실패. Stripe 웹훅이 정상 처리된 결과이므로 도메인 트랜잭션 롤백용 실패 리스너 패턴이
     * 아니라 성공 계열과 같은 {@code @ApplicationModuleListener}를 쓴다(package-info.java 참고).
     */
    @ApplicationModuleListener
    void onPaymentFailed(SubscriptionPaymentFailureRecordedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("payment_type", paymentType(event.paymentType()));
        properties.put("failure_code", event.failureCode());
        properties.put("amount", event.amount());
        properties.put("currency", event.currency());
        properties.put("failed_at", iso(event.failedAt()));
        analyticsEventPublisher.publish(PAYMENT_FAILED, event.memberId(), properties,
                AnalyticsInsertId.of(PAYMENT_FAILED, event.stripeEventId()));
    }

    /** 결제 실패 후 복구. recovery_attempt_number는 카운터 도입(V49) 이후 건만 센 값이다. */
    @ApplicationModuleListener
    void onPaymentRecovered(SubscriptionPaymentRecoveredEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_id", event.subscriptionId());
        properties.put("billing_interval", billingInterval(event.plan()));
        properties.put("recovery_attempt_number", event.recoveryAttemptNumber());
        properties.put("amount", event.amount());
        properties.put("currency", event.currency());
        properties.put("recovered_at", iso(event.recoveredAt()));
        analyticsEventPublisher.publish(PAYMENT_RECOVERED, event.memberId(), properties,
                AnalyticsInsertId.of(PAYMENT_RECOVERED, event.stripeEventId()));
    }

    /** 결제 수단 등록. 카드 정보는 설계상 싣지 않고 구독 상태만 남긴다. */
    @ApplicationModuleListener
    void onPaymentMethodUpdated(PaymentMethodUpdatedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("subscription_status", subscriptionStatus(event.subscriptionStatus()));
        properties.put("updated_at", iso(event.updatedAt()));
        analyticsEventPublisher.publish(PAYMENT_METHOD_UPDATED, event.memberId(), properties,
                AnalyticsInsertId.of(PAYMENT_METHOD_UPDATED, event.stripeEventId()));
    }

    /** 결제 유형은 PostHog 속성 표기(소문자)로 내보낸다. 판별하지 못한 경우는 속성에서 빠진다. */
    private String paymentType(SubscriptionPaymentType paymentType) {
        return paymentType == null ? null : paymentType.name().toLowerCase(Locale.ROOT);
    }

    /** 구독 상태도 소문자로 내보낸다. 살아 있는 구독이 없으면 속성에서 빠진다. */
    private String subscriptionStatus(SubscriptionStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    /** 플랜에서 결제 주기를 유도한다. 구독 이벤트의 플랜은 항상 프로 2종 중 하나다. */
    private String billingInterval(PlanType plan) {
        return switch (plan) {
            case PRO_MONTHLY -> "monthly";
            case PRO_YEARLY -> "yearly";
            case STARTER -> "none"; // 결제가 없는 기본 요금제 — 구독 이벤트에서는 나오지 않는다
        };
    }

    /** 시각은 PostHog에서 그대로 읽히도록 ISO-8601 문자열로 싣는다. 값이 없으면 속성에서 빠진다. */
    private String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
