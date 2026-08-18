package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.internal.domain.EntitlementBalance;
import com.atcrew.billing.internal.domain.MemberProductId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntitlementBalanceRepository extends JpaRepository<EntitlementBalance, MemberProductId> {

    List<EntitlementBalance> findByIdMemberId(String memberId);
}
