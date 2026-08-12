package com.atcrew.billing.internal.domain;

import com.atcrew.billing.Plan;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.common.id.UuidV7Generator;
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
 * 회원당 1개인 구독 상태 (docs/design/billing-module-design.md §2.1).
 *
 * <p>Stripe 연동 필드(stripeCustomerId/stripeSubscriptionId/lastEvent*)는 스키마에만 존재하며
 * Checkout·Webhook 연동이 붙는 후속 작업에서 채워진다.
 */
@Entity
@Table(name = "subscriptions")
@EntityListeners(AuditingEntityListener.class)
public class Subscription implements Persistable<String> {

    @Id
    private String id;

    private String memberId;

    @Enumerated(EnumType.STRING)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private String stripeCustomerId;

    private String stripeSubscriptionId;

    private Instant currentPeriodEnd;

    private boolean cancelAtPeriodEnd;

    @Enumerated(EnumType.STRING)
    private Plan pendingPlan;

    // Stripe event.created — 웹훅 순서 역전 방어용(§4.3 보조 방어).
    private Instant lastEventAt;

    private String lastEventId;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // 신규 여부를 명시하지 않으면 save()가 매번 merge()(선행 SELECT)로 동작한다.
    @Transient
    private boolean isNew = false;

    protected Subscription() {
    }

    public static Subscription create(String memberId, Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.id = UuidV7Generator.generate();
        subscription.memberId = memberId;
        subscription.plan = plan;
        subscription.status = status;
        subscription.isNew = true;
        return subscription;
    }

    /**
     * 프로 권한 판정.
     *
     * <p>plan과 status를 분리해 두었기 때문에(§2.1) 결제 실패 시 plan=PRO_MONTHLY, status=PAST_DUE가 되어
     * 게이팅은 스타터로 떨어지면서도 "프로 플랜 월간, 결제 실패" 표기가 동시에 성립한다.
     */
    public boolean isPro() {
        return plan != Plan.STARTER
                && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING);
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public Plan getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public Plan getPendingPlan() { return pendingPlan; }
    public Instant getLastEventAt() { return lastEventAt; }
    public String getLastEventId() { return lastEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
