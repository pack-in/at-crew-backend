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
import org.springframework.data.annotation.LastModifiedBy;
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

    // 지금까지 성공한 정기 갱신 결제 횟수(PA-09). 최초 결제는 세지 않으므로 첫 갱신이 1이다.
    @Column(name = "renewal_number", nullable = false)
    private int renewalNumber;

    // 결제 실패 후 복구(재결제 성공)에 성공한 횟수(PA-09). 첫 복구가 1이다.
    @Column(name = "recovery_attempt_number", nullable = false)
    private int recoveryAttemptNumber;

    // 상태가 처음 ACTIVE가 된 시각. null이면 한 번도 활성화된 적이 없다 — incomplete로 시작해
    // 아직 정상화되지 않은 구독이 여기 해당한다(code-review 2026-10-06).
    @Column(name = "activated_at")
    private Instant activatedAt;

    // 금액이 0보다 큰 결제가 처음 성공한 시각. 무료 체험 시작의 $0 인보이스는 여기 안 든다 —
    // 체험 종료 후 첫 실결제를 "최초 결제"로 보려면 활성화 시각과는 별개로 추적해야 한다.
    @Column(name = "first_payment_at")
    private Instant firstPaymentAt;

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
    // 변경 주체(이슈 #138). 회원 ID이거나 SYSTEM, 운영자 수동 UPDATE는 "ops:<담당자>".
    // 기록 시작 전 행은 NULL로 남는다.
    @LastModifiedBy
    @Column(name = "last_modified_by", length = 64)
    private String lastModifiedBy;

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
        subscription.renewalNumber = 0;
        subscription.recoveryAttemptNumber = 0;
        // 트라이얼 포함 ACTIVE로 바로 시작하면 이 시점이 최초 활성화다. incomplete/PAST_DUE로
        // 시작하면 null로 둔다 — 나중에 정상화될 때 markActivatedIfFirstTime()이 채운다.
        subscription.activatedAt = status.grantsPro() ? stripeUpdatedAt : null;
        subscription.firstPaymentAt = null;
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

    /**
     * 상태가 ACTIVE로 바뀌는 시점에 호출한다. 이미 한 번이라도 활성화됐던 적이 있으면(=복구) 아무
     * 것도 하지 않는다 — null → 값 한 방향으로만 움직인다.
     *
     * @return 이번이 최초 활성화였으면 true(복구가 아니라 "활성화" 이벤트를 내야 한다는 뜻)
     */
    public boolean markActivatedIfFirstTime(Instant at) {
        if (activatedAt != null) {
            return false;
        }
        activatedAt = at;
        return true;
    }

    /**
     * 금액이 0보다 큰 결제가 성공하는 시점에 호출한다. 이미 한 번이라도 실결제가 있었으면
     * 아무것도 하지 않는다.
     *
     * @return 이번이 최초 실결제였으면 true(최초/갱신 판정에 쓴다)
     */
    public boolean markFirstPaymentIfFirstTime(Instant at) {
        if (firstPaymentAt != null) {
            return false;
        }
        firstPaymentAt = at;
        return true;
    }

    /**
     * 정기 갱신 결제가 성공했다(PA-09).
     *
     * @return 올린 뒤의 값 — 이번 결제가 몇 번째 갱신인지
     */
    public int incrementRenewalNumber() {
        return ++this.renewalNumber;
    }

    /**
     * 결제 실패 상태에서 복구됐다(PA-09).
     *
     * @return 올린 뒤의 값 — 이번 복구가 몇 번째인지
     */
    public int incrementRecoveryAttemptNumber() {
        return ++this.recoveryAttemptNumber;
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public PlanType getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public int getRenewalNumber() { return renewalNumber; }
    public int getRecoveryAttemptNumber() { return recoveryAttemptNumber; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getFirstPaymentAt() { return firstPaymentAt; }
    public Instant getStripeUpdatedAt() { return stripeUpdatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
