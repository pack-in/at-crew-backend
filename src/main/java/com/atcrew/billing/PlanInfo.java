package com.atcrew.billing;

import java.time.Instant;

public record PlanInfo(
        Plan plan,                   // 가입한 상품 — 결제 실패 상태에서도 유지된다
        SubscriptionStatus status,   // 결제 상태
        Instant currentPeriodEnd,    // 현재 결제 주기 종료 시각 (구독 이력이 없으면 null)
        boolean cancelAtPeriodEnd,   // 주기 종료 시 해지 예약 여부
        Plan pendingPlan             // 월↔연 주기 변경 예약 (없으면 null)
) {
}
