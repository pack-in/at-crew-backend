package com.atcrew.billing.internal.domain;

import com.atcrew.billing.BillingProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

/** 단건 상품 보유 개수 테이블의 복합 기본키 (회원 ID, 상품). */
@Embeddable
public class MemberProductId implements Serializable {

    @Column(name = "member_id", length = 36, nullable = false)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", length = 30, nullable = false)
    private BillingProduct product;

    protected MemberProductId() {
    }

    public MemberProductId(String memberId, BillingProduct product) {
        this.memberId = memberId;
        this.product = product;
    }

    public String getMemberId() { return memberId; }
    public BillingProduct getProduct() { return product; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberProductId other)) {
            return false;
        }
        return Objects.equals(memberId, other.memberId) && product == other.product;
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, product);
    }
}
