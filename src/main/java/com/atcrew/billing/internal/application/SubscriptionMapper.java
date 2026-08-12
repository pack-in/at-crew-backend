package com.atcrew.billing.internal.application;

import com.atcrew.billing.Plan;
import com.atcrew.billing.PlanInfo;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.Subscription;

class SubscriptionMapper {

    private SubscriptionMapper() {
    }

    static PlanInfo toInfo(Subscription subscription) {
        return new PlanInfo(
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getPendingPlan()
        );
    }

    /** 구독 레코드가 없는 회원의 기본값 — 조회가 null 없이 항상 스타터로 떨어지게 한다. */
    static PlanInfo starterDefault() {
        return new PlanInfo(Plan.STARTER, SubscriptionStatus.NONE, null, false, null);
    }
}
