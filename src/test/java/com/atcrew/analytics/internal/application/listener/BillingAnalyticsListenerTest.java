package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionActivatedEvent;
import com.atcrew.billing.SubscriptionBillingIntervalChangedEvent;
import com.atcrew.billing.SubscriptionCancelRequestRevokedEvent;
import com.atcrew.billing.SubscriptionCancelRequestedEvent;
import com.atcrew.billing.SubscriptionCheckoutStartedEvent;
import com.atcrew.billing.SubscriptionEndReason;
import com.atcrew.billing.SubscriptionEndedEvent;
import com.atcrew.billing.SubscriptionReactivatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * billing 구독 이벤트를 PostHog 이벤트로 옮기는 리스너 단위 테스트(PA-08).
 */
class BillingAnalyticsListenerTest {

    private static final String MEMBER_ID = "member-001";
    private static final String SUBSCRIPTION_ID = "sub_1";
    private static final String STRIPE_EVENT_ID = "evt_1";
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-05T00:00:00Z");

    AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);

    BillingAnalyticsListener listener = new BillingAnalyticsListener(analyticsEventPublisher);

    @Test
    void 체크아웃_시작은_세션_ID로_중복_집계를_막는다() {
        listener.onCheckoutStarted(new SubscriptionCheckoutStartedEvent(MEMBER_ID, "cs_1",
                BillingProduct.PRO_YEARLY, 30, OCCURRED_AT));

        assertThat(captured("subscription_checkout_started", "subscription_checkout_started:cs_1"))
                .containsEntry("checkout_session_id", "cs_1")
                .containsEntry("product", "PRO_YEARLY")
                .containsEntry("trial_days", 30)
                .containsEntry("started_at", "2026-10-05T00:00:00Z");
    }

    @Test
    void 활성화는_플랜에서_결제_주기를_유도해_싣는다() {
        listener.onActivated(new SubscriptionActivatedEvent(MEMBER_ID, SUBSCRIPTION_ID,
                PlanType.PRO_MONTHLY, OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_activated", "subscription_activated:evt_1"))
                .containsEntry("subscription_id", SUBSCRIPTION_ID)
                .containsEntry("billing_interval", "monthly")
                .containsEntry("activated_at", "2026-10-05T00:00:00Z");
    }

    @Test
    void 재활성화는_직전_종료_이후_일수를_싣는다() {
        listener.onReactivated(new SubscriptionReactivatedEvent(MEMBER_ID, SUBSCRIPTION_ID,
                PlanType.PRO_YEARLY, 14L, OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_reactivated", "subscription_reactivated:evt_1"))
                .containsEntry("billing_interval", "yearly")
                .containsEntry("days_since_end", 14L);
    }

    @Test
    void 종료_시각을_모르는_재활성화는_일수_속성을_비워_보낸다() {
        listener.onReactivated(new SubscriptionReactivatedEvent(MEMBER_ID, SUBSCRIPTION_ID,
                PlanType.PRO_MONTHLY, null, OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_reactivated", "subscription_reactivated:evt_1"))
                .containsEntry("days_since_end", null);
    }

    @Test
    void 취소_예약은_혜택_종료_시각을_싣는다() {
        listener.onCancelRequested(new SubscriptionCancelRequestedEvent(MEMBER_ID, SUBSCRIPTION_ID,
                PlanType.PRO_MONTHLY, Instant.parse("2026-11-04T00:00:00Z"), OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_cancel_requested", "subscription_cancel_requested:evt_1"))
                .containsEntry("current_period_end", "2026-11-04T00:00:00Z")
                .containsEntry("requested_at", "2026-10-05T00:00:00Z");
    }

    @Test
    void 취소_철회도_혜택_종료_시각을_싣는다() {
        listener.onCancelRequestRevoked(new SubscriptionCancelRequestRevokedEvent(MEMBER_ID, SUBSCRIPTION_ID,
                PlanType.PRO_MONTHLY, Instant.parse("2026-11-04T00:00:00Z"), OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_cancel_request_revoked",
                "subscription_cancel_request_revoked:evt_1"))
                .containsEntry("current_period_end", "2026-11-04T00:00:00Z")
                .containsEntry("revoked_at", "2026-10-05T00:00:00Z");
    }

    @Test
    void 종료_사유는_소문자_문자열로_싣는다() {
        listener.onEnded(new SubscriptionEndedEvent(MEMBER_ID, SUBSCRIPTION_ID, PlanType.PRO_MONTHLY,
                SubscriptionEndReason.PAYMENT_FAILED, OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_ended", "subscription_ended:evt_1"))
                .containsEntry("end_reason", "payment_failed")
                .containsEntry("ended_at", "2026-10-05T00:00:00Z");
    }

    @Test
    void 결제_주기_변경은_변경_전후_주기를_싣는다() {
        listener.onBillingIntervalChanged(new SubscriptionBillingIntervalChangedEvent(MEMBER_ID,
                SUBSCRIPTION_ID, PlanType.PRO_MONTHLY, PlanType.PRO_YEARLY, OCCURRED_AT, STRIPE_EVENT_ID));

        assertThat(captured("subscription_billing_interval_changed",
                "subscription_billing_interval_changed:evt_1"))
                .containsEntry("previous_interval", "monthly")
                .containsEntry("new_interval", "yearly")
                .containsEntry("effective_at", "2026-10-05T00:00:00Z");
    }

    private Map<String, Object> captured(String eventName, String insertId) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq(eventName), eq(MEMBER_ID), properties.capture(),
                eq(insertId));
        return properties.getValue();
    }
}
