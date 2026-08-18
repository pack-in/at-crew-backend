package com.atcrew.billing.internal.application;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.internal.domain.EntitlementBalance;
import com.atcrew.billing.internal.domain.EntitlementLedger;
import com.atcrew.billing.internal.domain.LedgerReason;
import com.atcrew.billing.internal.domain.MemberProductId;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.billing.internal.persistence.EntitlementLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 단건 게시 상품 보유 개수의 증감을 담당한다. 잔량 변경은 반드시 원장 기록과 같은 트랜잭션에서 이뤄진다.
 */
@Service
class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    private final EntitlementBalanceRepository balanceRepository;
    private final EntitlementLedgerRepository ledgerRepository;

    EntitlementService(EntitlementBalanceRepository balanceRepository,
            EntitlementLedgerRepository ledgerRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /** 결제 완료로 1개 지급한다(요금제-R06). */
    @Transactional
    void grant(String memberId, BillingProduct product, String stripeEventId, String paymentIntentId) {
        EntitlementBalance balance = balanceRepository.findById(new MemberProductId(memberId, product))
                .orElseGet(() -> EntitlementBalance.create(memberId, product));
        balance.add(1);
        balanceRepository.save(balance);
        ledgerRepository.save(EntitlementLedger.of(memberId, product, 1,
                LedgerReason.PURCHASE, stripeEventId, paymentIntentId));
    }

    /** 게시 성공 시 1개 차감한다. 잔량이 없으면 예외를 던져 호출한 게시 트랜잭션을 되돌린다. */
    @Transactional
    void consume(String memberId, BillingProduct product, String refId) {
        EntitlementBalance balance = balanceRepository.findById(new MemberProductId(memberId, product))
                .orElseGet(() -> EntitlementBalance.create(memberId, product));
        balance.consumeOne();
        balanceRepository.save(balance);
        ledgerRepository.save(EntitlementLedger.of(memberId, product, -1,
                LedgerReason.CONSUME, null, refId));
    }

    /**
     * 환불된 결제로 지급했던 권한을 회수한다. 이미 사용한 경우에는 회수하지 않고 로그만 남긴다(D12) —
     * 잔량을 음수로 만들면 이후 구매분까지 소급 차감돼 회원에게 부당하다.
     */
    @Transactional
    void revoke(String memberId, BillingProduct product, String stripeEventId, String refId) {
        EntitlementBalance balance = balanceRepository.findById(new MemberProductId(memberId, product))
                .orElseGet(() -> EntitlementBalance.create(memberId, product));
        if (!balance.revokeOne()) {
            log.warn("환불 회수 불가 — 이미 사용된 권한입니다. memberId={}, product={}, refId={}",
                    memberId, product, refId);
            return;
        }
        balanceRepository.save(balance);
        ledgerRepository.save(EntitlementLedger.of(memberId, product, -1,
                LedgerReason.REFUND_REVOKE, stripeEventId, refId));
    }

    @Transactional(readOnly = true)
    int getQuantity(String memberId, BillingProduct product) {
        return balanceRepository.findById(new MemberProductId(memberId, product))
                .map(EntitlementBalance::getQuantity)
                .orElse(0);
    }

    /** 단건 상품 3종의 보유 개수. 보유 이력이 없는 상품도 0으로 채워 내려간다. */
    @Transactional(readOnly = true)
    Map<BillingProduct, Integer> getQuantities(String memberId) {
        Map<BillingProduct, Integer> quantities = new EnumMap<>(BillingProduct.class);
        for (BillingProduct product : BillingProduct.values()) {
            if (!product.isSubscription()) {
                quantities.put(product, 0);
            }
        }
        List<EntitlementBalance> balances = balanceRepository.findByIdMemberId(memberId);
        for (EntitlementBalance balance : balances) {
            quantities.put(balance.getProduct(), balance.getQuantity());
        }
        return quantities;
    }
}
