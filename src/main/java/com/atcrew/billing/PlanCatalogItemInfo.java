package com.atcrew.billing;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요금제 페이지(PLAN-P01) 카드 1건 — 정적 카탈로그다. Stripe Price와 연동되지 않으며
 * 결제 자체는 이번 마일스톤 범위 밖이다(docs/design/billing-module-design.md §5, §3 "Stripe 실연동은 후속").
 */
@Schema(description = "요금제 페이지 카드 1건. GET /api/billing/plans는 이 카탈로그를 하드코딩된 값으로 "
        + "그대로 반환한다 — 실제 결제(Checkout)는 이번 마일스톤 범위 밖이라 이 정보만으로는 구매를 진행할 수 없다")
public record PlanCatalogItemInfo(
        @Schema(description = "플랜 식별자") Plan plan,
        @Schema(description = "카드에 표시할 이름", example = "프로 연간") String name,
        @Schema(description = "결제 주기 — 스타터는 null", example = "YEARLY", nullable = true) String billingCycle,
        @Schema(description = "판매가(원, VAT 포함 표기)", example = "75000") int price,
        @Schema(description = "정가(원) — 할인 없으면 price와 동일", example = "150000") int originalPrice,
        @Schema(description = "카드 배지 문구 — 없으면 null", example = "2개월 무료", nullable = true) String badge
) {
}
