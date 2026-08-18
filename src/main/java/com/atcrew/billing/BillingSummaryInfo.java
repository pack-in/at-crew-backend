package com.atcrew.billing;

import java.time.Instant;
import java.util.Map;

/**
 * 설정 &gt; 요금제 및 결제 탭 상태(설정-R03).
 *
 * @param plan               현재 플랜. 결제 실패·취소 시 STARTER
 * @param status             구독 상태. 구독 이력이 없으면 null
 * @param currentPeriodEnd   현재 결제 주기 종료 시각(UTC). 구독이 없으면 null
 * @param cancelAtPeriodEnd  주기 종료 시 취소 예약 여부
 * @param balances           단건 상품별 보유 개수. 보유가 없는 상품도 0으로 내려간다
 */
public record BillingSummaryInfo(
        PlanType plan,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Map<BillingProduct, Integer> balances
) {
}
