package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.internal.domain.EntitlementLedger;
import com.atcrew.billing.internal.domain.LedgerReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntitlementLedgerRepository extends JpaRepository<EntitlementLedger, String> {

    /**
     * 결제 건(Stripe PaymentIntent ID)으로 지급 이력을 찾는다 — 환불 회수 시 어떤 회원의 어떤 상품을
     * 되돌릴지 판단하는 근거다. 팀원모집글처럼 한 결제로 2종을 지급한 경우 여러 건이 나온다.
     */
    List<EntitlementLedger> findByRefIdAndReason(String refId, LedgerReason reason);
}
