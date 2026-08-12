package com.atcrew.billing.internal.web;

import com.atcrew.billing.Plan;
import com.atcrew.billing.PlanCatalogItemInfo;
import com.atcrew.billing.PlanInfo;
import com.atcrew.billing.PlanService;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 요금제 조회 API (docs/design/billing-module-design.md §3, §5).
 *
 * <p>이번 마일스톤은 읽기 전용이다 — Stripe Checkout/Customer Portal/Webhook은 후속 범위라
 * 구독 변경 엔드포인트가 없다. 플랜 승급은 운영 도구(웹훅 연동 전까지는 DB 직접 반영)로만 가능하다.
 */
@Tag(name = "요금제", description = "요금제 카드 목록·내 플랜 조회 API (구독 변경은 후속 범위)")
@RestController
@RequestMapping("/api/billing")
class BillingController {

    // docs/design/billing-module-design.md §5 — Stripe Price와 연동되지 않은 정적 카탈로그.
    private static final List<PlanCatalogItemInfo> CATALOG = List.of(
            new PlanCatalogItemInfo(Plan.STARTER, "스타터", null, 0, 0, null),
            new PlanCatalogItemInfo(Plan.PRO_MONTHLY, "프로 월간", "MONTHLY", 7_500, 15_000, null),
            new PlanCatalogItemInfo(Plan.PRO_YEARLY, "프로 연간", "YEARLY", 75_000, 150_000, "2개월 무료")
    );

    private final PlanService planService;
    private final SecurityUtils securityUtils;

    BillingController(PlanService planService, SecurityUtils securityUtils) {
        this.planService = planService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "요금제 카드 목록", description = "비로그인 요금제 페이지(PLAN-P01)용 정적 카탈로그입니다. 스타터 카드는 이 목록에서만 노출하고, 로그인 후 설정 탭에는 프로 카드만 노출하는 건 프론트 책임입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/plans")
    public ApiResponse<List<PlanCatalogItemInfo>> getPlans() {
        return ApiResponse.success(CATALOG);
    }

    @Operation(summary = "내 플랜 조회", description = "설정 > 요금제 및 결제 탭 상단 상태 표시용입니다. 구독 레코드가 없으면 스타터 기본값을 반환합니다(404 없음).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    @GetMapping("/me")
    public ApiResponse<PlanInfo> getMyPlan() {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(planService.getPlan(memberId));
    }
}
