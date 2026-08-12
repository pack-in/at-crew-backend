package com.atcrew.billing.internal.application;

import com.atcrew.billing.PlanInfo;
import com.atcrew.billing.PlanService;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.exception.BillingErrorCode;
import com.atcrew.billing.internal.exception.BillingException;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PlanServiceImpl implements PlanService {

    // 스타터 작품 등록 상한 (docs/design/billing-module-design.md §4.4) — artwork 모듈이 소비한다.
    private static final int STARTER_ARTWORK_LIMIT = 4;

    private final SubscriptionRepository subscriptionRepository;

    PlanServiceImpl(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanInfo getPlan(String memberId) {
        return subscriptionRepository.findByMemberId(memberId)
                .map(SubscriptionMapper::toInfo)
                .orElseGet(SubscriptionMapper::starterDefault);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPro(String memberId) {
        return holdsPro(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertPro(String memberId) {
        if (!holdsPro(memberId)) {
            throw new BillingException(BillingErrorCode.PRO_PLAN_REQUIRED, "memberId=" + memberId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int artworkLimit(String memberId) {
        return holdsPro(memberId) ? Integer.MAX_VALUE : STARTER_ARTWORK_LIMIT;
    }

    private boolean holdsPro(String memberId) {
        return subscriptionRepository.findByMemberId(memberId)
                .map(Subscription::isPro)
                .orElse(false);
    }
}
