package com.atcrew.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "회원의 현재 플랜 상태. 구독 레코드가 없는 회원(가입 직후 등)도 이 응답 자체는 항상 200으로 "
        + "내려가며, 그 경우 plan=STARTER, status=NONE으로 채워진다")
public record PlanInfo(
        @Schema(description = "가입한 상품 — 결제 실패 상태에서도 유지된다(게이팅 판정은 status를 함께 본다)")
        Plan plan,
        @Schema(description = "결제 상태 — 구독 레코드가 없으면 NONE", example = "ACTIVE")
        SubscriptionStatus status,
        @Schema(description = "현재 결제 주기 종료 시각(ISO 8601) — 구독 이력이 없으면 null", nullable = true)
        Instant currentPeriodEnd,
        @Schema(description = "주기 종료 시 해지 예약 여부")
        boolean cancelAtPeriodEnd,
        @Schema(description = "월↔연 주기 변경 예약 — 없으면 null", nullable = true)
        Plan pendingPlan
) {
}
