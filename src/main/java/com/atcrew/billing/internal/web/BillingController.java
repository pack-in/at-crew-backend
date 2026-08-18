package com.atcrew.billing.internal.web;

import com.atcrew.billing.BillingService;
import com.atcrew.billing.BillingSummaryInfo;
import com.atcrew.billing.CatalogItemInfo;
import com.atcrew.billing.CheckoutSessionInfo;
import com.atcrew.billing.PortalSessionInfo;
import com.atcrew.billing.internal.web.dto.CreateCheckoutSessionRequest;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 요금제·결제 API. 카드 입력·구독 취소·결제수단 변경 화면은 만들지 않는다 —
 * 결제는 Stripe Checkout, 구독 관리는 Stripe Customer Portal로 위임한다(D3).
 */
@Tag(name = "결제/구독", description = "요금제 카탈로그·구독 상태 조회, Stripe Checkout·Customer Portal 진입 API")
@RestController
@RequestMapping("/api/billing")
class BillingController {

    private final BillingService billingService;
    private final SecurityUtils securityUtils;

    BillingController(BillingService billingService, SecurityUtils securityUtils) {
        this.billingService = billingService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "요금제 카탈로그 조회",
            description = "상품별 가격(USD 센트)과 버튼 상태를 반환합니다. 비로그인도 호출할 수 있으며, "
                    + "로그인 상태면 현재 구독을 반영해 cta가 CURRENT·CHANGE·UNAVAILABLE로 내려갑니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/catalog")
    public ApiResponse<List<CatalogItemInfo>> getCatalog() {
        return ApiResponse.success(billingService.getCatalog(getOptionalMemberId()));
    }

    @Operation(summary = "내 결제 상태 조회",
            description = "현재 플랜·구독 상태·다음 결제일과 단건 게시 상품 보유 개수를 반환합니다. "
                    + "결제 실패 상태(PAST_DUE)면 설정 탭 상단 배너를 노출하는 데 사용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증 필요 (UNAUTHENTICATED)")
    })
    @GetMapping("/me")
    public ApiResponse<BillingSummaryInfo> getMySummary() {
        return ApiResponse.success(billingService.getSummary(securityUtils.getCurrentMemberId()));
    }

    @Operation(summary = "결제 페이지 진입",
            description = "Stripe Checkout 세션을 생성하고 결제 페이지 URL을 반환합니다. "
                    + "프론트는 반환된 URL로 이동시키고, 결제 완료 반영은 웹훅 기준이므로 복귀 후 /api/billing/me를 폴링합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "상품 유형 오류 (INVALID_PRODUCT)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증 필요 (UNAUTHENTICATED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "기업 계정은 구독 불가 (SUBSCRIPTION_NOT_ALLOWED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 이용 중인 플랜 (ALREADY_SUBSCRIBED) / 플랜 변경은 포털에서 (SUBSCRIPTION_CHANGE_VIA_PORTAL)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
                    description = "Stripe 요청 실패 (STRIPE_REQUEST_FAILED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "상품 미설정 (PRICE_NOT_CONFIGURED)")
    })
    @PostMapping("/checkout-sessions")
    public ApiResponse<CheckoutSessionInfo> createCheckoutSession(
            @RequestBody @Valid CreateCheckoutSessionRequest request) {
        return ApiResponse.success(billingService.createCheckoutSession(
                securityUtils.getCurrentMemberId(), request.product()));
    }

    @Operation(summary = "결제 관리 페이지 진입",
            description = "Stripe Customer Portal 세션을 생성합니다. 구독 취소·주기 변경·결제수단 변경·영수증 조회는 "
                    + "전부 이 화면에서 처리하며, 설정 탭의 [결제 내역 바로가기]도 같은 URL입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증 필요 (UNAUTHENTICATED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "결제 이력 없음 (CUSTOMER_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
                    description = "Stripe 요청 실패 (STRIPE_REQUEST_FAILED)")
    })
    @PostMapping("/portal-sessions")
    public ApiResponse<PortalSessionInfo> createPortalSession() {
        return ApiResponse.success(billingService.createPortalSession(securityUtils.getCurrentMemberId()));
    }

    /** 카탈로그는 비로그인도 조회하므로 인증 정보가 없으면 null을 넘긴다. */
    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        return null;
    }
}
