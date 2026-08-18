package com.atcrew.billing.internal.domain;

import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Stripe 구독 미러. 구독 하나당 한 행이며 취소된 구독도 이력으로 남는다.
 *
 * <p>웹훅은 순서가 뒤바뀌어 도착할 수 있으므로, Stripe가 준 이벤트 시각(stripeUpdatedAt)보다
 * 오래된 이벤트는 무시한다(D16).
 */
@Entity
@Table(name = "billing_subscriptions")
@EntityListeners(AuditingEntityListener.class)
public class Subscription implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "member_id", length = 36, nullable = false)
    private String memberId;

    @Column(name = "stripe_subscription_id", length = 64, nullable = false)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanType plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "stripe_updated_at", nullable = false)
    private Instant stripeUpdatedAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = false;

    protected Subscription() {
    }

    public static Subscription create(String memberId, String stripeSubscriptionId, PlanType plan,
            SubscriptionStatus status, Instant currentPeriodEnd, boolean cancelAtPeriodEnd,
            Instant stripeUpdatedAt) {
        Subscription subscription = new Subscription();
        subscription.id = UuidV7Generator.generate();
        subscription.memberId = memberId;
        subscription.stripeSubscriptionId = stripeSubscriptionId;
        subscription.plan = plan;
        subscription.status = status;
        subscription.currentPeriodEnd = currentPeriodEnd;
        subscription.cancelAtPeriodEnd = cancelAtPeriodEnd;
        subscription.stripeUpdatedAt = stripeUpdatedAt;
        subscription.isNew = true;
        return subscription;
    }

    /**
     * Stripe 상태를 반영한다. 이미 반영된 이벤트보다 오래된 이벤트면 아무것도 바꾸지 않는다.
     *
     * @return 실제로 갱신했으면 true
     */
    public boolean sync(PlanType plan, SubscriptionStatus status, Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd, Instant stripeUpdatedAt) {
        if (stripeUpdatedAt.isBefore(this.stripeUpdatedAt)) {
            return false;
        }
        this.plan = plan;
        this.status = status;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.stripeUpdatedAt = stripeUpdatedAt;
        return true;
    }

    public boolean grantsPro() {
        return status.grantsPro();
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public PlanType getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public Instant getStripeUpdatedAt() { return stripeUpdatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
