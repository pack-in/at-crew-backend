package com.atcrew.billing.internal.domain;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.internal.exception.BillingErrorCode;
import com.atcrew.billing.internal.exception.BillingException;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

/**
 * 단건 게시 상품 보유 개수. 구매 시 +1, 게시 성공 시 -1이며 만료되지 않는다(요금제-R06).
 *
 * <p>동시 게시로 인한 이중 차감은 낙관적 락(version)으로 막는다 — 경합한 쪽은 재시도 없이 실패한다.
 */
@Entity
@Table(name = "billing_entitlement_balances")
public class EntitlementBalance implements Persistable<MemberProductId> {

    @EmbeddedId
    private MemberProductId id;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    @Transient
    private boolean isNew = false;

    protected EntitlementBalance() {
    }

    public static EntitlementBalance create(String memberId, BillingProduct product) {
        EntitlementBalance balance = new EntitlementBalance();
        balance.id = new MemberProductId(memberId, product);
        balance.quantity = 0;
        balance.isNew = true;
        return balance;
    }

    public void add(int amount) {
        this.quantity += amount;
    }

    /** 1개 차감. 잔량이 없으면 게시를 막는다. */
    public void consumeOne() {
        if (quantity <= 0) {
            throw new BillingException(BillingErrorCode.ENTITLEMENT_REQUIRED,
                    "memberId=" + id.getMemberId() + ", product=" + id.getProduct());
        }
        this.quantity -= 1;
    }

    /**
     * 환불된 권한을 회수한다. 이미 사용해 잔량이 없으면 음수로 만들지 않고 회수하지 않는다(D12).
     *
     * @return 실제로 회수했으면 true
     */
    public boolean revokeOne() {
        if (quantity <= 0) {
            return false;
        }
        this.quantity -= 1;
        return true;
    }

    @Override
    public MemberProductId getId() { return id; }
    public String getMemberId() { return id.getMemberId(); }
    public BillingProduct getProduct() { return id.getProduct(); }
    public int getQuantity() { return quantity; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
