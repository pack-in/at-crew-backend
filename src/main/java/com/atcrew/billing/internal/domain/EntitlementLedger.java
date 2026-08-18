package com.atcrew.billing.internal.domain;

import com.atcrew.billing.BillingProduct;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 잔량 변동 원장. append-only이며 수정·삭제하지 않는다 — 환불 회수와 "게시 실패 시 미차감" 검증의 근거다.
 */
@Entity
@Table(name = "billing_entitlement_ledgers")
@EntityListeners(AuditingEntityListener.class)
public class EntitlementLedger implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "member_id", length = 36, nullable = false)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BillingProduct product;

    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerReason reason;

    /** 지급·회수의 근거가 된 Stripe 이벤트. 차감(CONSUME)에는 없다. */
    @Column(name = "stripe_event_id", length = 64)
    private String stripeEventId;

    /** 차감 대상 식별자(게시글 ID 등). */
    @Column(name = "ref_id", length = 64)
    private String refId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    protected EntitlementLedger() {
    }

    public static EntitlementLedger of(String memberId, BillingProduct product, int delta,
            LedgerReason reason, String stripeEventId, String refId) {
        EntitlementLedger ledger = new EntitlementLedger();
        ledger.id = UuidV7Generator.generate();
        ledger.memberId = memberId;
        ledger.product = product;
        ledger.delta = delta;
        ledger.reason = reason;
        ledger.stripeEventId = stripeEventId;
        ledger.refId = refId;
        ledger.isNew = true;
        return ledger;
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public BillingProduct getProduct() { return product; }
    public int getDelta() { return delta; }
    public LedgerReason getReason() { return reason; }
    public String getStripeEventId() { return stripeEventId; }
    public String getRefId() { return refId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
