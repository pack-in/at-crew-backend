package com.atcrew.billing.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * 회원 ↔ Stripe Customer 매핑. 탈퇴 시에도 삭제하지 않는다 — 환불·분쟁 대응에 결제 이력이 필요하다(D13).
 */
@Entity
@Table(name = "billing_customers")
@EntityListeners(AuditingEntityListener.class)
public class BillingCustomer implements Persistable<String> {

    @Id
    @Column(name = "member_id", length = 36)
    private String memberId;

    @Column(name = "stripe_customer_id", length = 64, nullable = false)
    private String stripeCustomerId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    protected BillingCustomer() {
    }

    public static BillingCustomer create(String memberId, String stripeCustomerId) {
        BillingCustomer customer = new BillingCustomer();
        customer.memberId = memberId;
        customer.stripeCustomerId = stripeCustomerId;
        customer.isNew = true;
        return customer;
    }

    @Override
    public String getId() { return memberId; }
    public String getMemberId() { return memberId; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
