package com.atcrew.portfolio.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.portfolio.PortfolioArtworkCardInfo;
import com.atcrew.portfolio.PortfolioSharedInfo;
import com.atcrew.portfolio.PortfolioSnapshotDetailInfo;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 포트폴리오 공개 주소 {@code /{handle}/portfolio/{code}} 열람 API (docs/design/member-handle-design.md §2.2).
 *
 * <p>URL은 {@code /api/members} 아래지만 포트폴리오 자원이라 portfolio 모듈에 둔다 — member 모듈이 portfolio를 알면
 * 의존 방향이 뒤집힌다. handle 해석은 {@code MemberService.resolveHandle}을 거친다. 접근 정책·응답은 기존
 * {@code /api/portfolios/shared/*} 3종과 같다.
 */
@Tag(name = "포트폴리오", description = "작가 페이지·공유 포트폴리오 생성·조회·수정·삭제 API")
@Validated
@RestController
@RequestMapping("/api/members/{handle}/portfolios/{code}")
class PortfolioPublicCodeController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    // 경로 변수 handle — 옛 handle(최대 30자, 하이픈 포함)까지 받는다. 새 규칙은 생성·변경 시에만 적용한다(member-handle-design §2.2).
    private static final String HANDLE_PATTERN = "^[A-Za-z0-9_-]{1,50}$";
    private static final String PUBLIC_CODE_PATTERN = "^[a-z0-9]{6}$";
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final PortfolioServiceImpl portfolioService;

    PortfolioPublicCodeController(PortfolioServiceImpl portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Operation(summary = "공유 포트폴리오 열람 (공개 주소)",
            description = "인증이 필요 없습니다. handle은 옛 handle도 받으며, 응답의 ownerHandle은 항상 현재 handle입니다 — "
                    + "URL의 handle과 대소문자 무시 비교로 다르면 현재 handle 주소로 리다이렉트합니다. 작가 페이지는 코드가 없어 "
                    + "이 경로로 열리지 않습니다. X-Robots-Tag: noindex 헤더를 함께 내려줍니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "handle·code 형식 오류(COMMON_INVALID_INPUT)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "handle이 쓰인 적 없음(MEMBER_NOT_FOUND), 그 작가에게 해당 코드의 포트폴리오가 없음(PORTFOLIO_NOT_FOUND)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
            description = "탈퇴한 회원의 handle(MEMBER_WITHDRAWN), 차단된 포트폴리오(PORTFOLIO_BLOCKED)")
    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioSharedInfo>> getPortfolio(
            @Parameter(description = "작가 handle (옛 handle 포함)") @PathVariable
            @Pattern(regexp = HANDLE_PATTERN, message = "handle 형식이 올바르지 않습니다") String handle,
            @Parameter(description = "포트폴리오 공개 코드 (영문 소문자·숫자 6자)") @PathVariable
            @Pattern(regexp = PUBLIC_CODE_PATTERN, message = "포트폴리오 코드 형식이 올바르지 않습니다") String code) {
        return noIndex(ApiResponse.success(portfolioService.getSharedPortfolioByCode(handle, code)));
    }

    @Operation(summary = "공유 포트폴리오 작품 목록 (공개 주소)",
            description = "인증 선택 — 토큰을 보내면 성인 표시 설정과 본인 예외가 반영됩니다. 작품 순서는 업로드순(오래된순) 고정이며 커서는 마지막 항목의 순번입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "handle·code 형식 오류(COMMON_INVALID_INPUT), 커서 형식 오류(INVALID_CURSOR)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "handle이 쓰인 적 없음(MEMBER_NOT_FOUND), 그 작가에게 해당 코드의 포트폴리오가 없음(PORTFOLIO_NOT_FOUND)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
            description = "탈퇴한 회원의 handle(MEMBER_WITHDRAWN), 차단된 포트폴리오(PORTFOLIO_BLOCKED)")
    @GetMapping("/artworks")
    public ResponseEntity<ApiResponse<CursorPage<PortfolioArtworkCardInfo>>> getPortfolioArtworks(
            @Parameter(description = "작가 handle (옛 handle 포함)") @PathVariable
            @Pattern(regexp = HANDLE_PATTERN, message = "handle 형식이 올바르지 않습니다") String handle,
            @Parameter(description = "포트폴리오 공개 코드 (영문 소문자·숫자 6자)") @PathVariable
            @Pattern(regexp = PUBLIC_CODE_PATTERN, message = "포트폴리오 코드 형식이 올바르지 않습니다") String code,
            @Parameter(description = "커서 (직전 페이지 마지막 항목의 순번)") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        return noIndex(ApiResponse.success(portfolioService.getSharedPortfolioArtworksByCode(
                handle, code, cursor, resolveSize(size), getOptionalMemberId())));
    }

    @Operation(summary = "고정형 스냅샷 상세 열람 (공개 주소)",
            description = "인증이 필요 없습니다. 고정형(SNAPSHOT) 포트폴리오 안에서만 열리며 생성 시점에 얼린 값만 내려줍니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "handle·code·스냅샷 ID 형식 오류(COMMON_INVALID_INPUT)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "handle이 쓰인 적 없음(MEMBER_NOT_FOUND), 포트폴리오·스냅샷이 없거나 고정형이 아님(PORTFOLIO_NOT_FOUND)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
            description = "탈퇴한 회원의 handle(MEMBER_WITHDRAWN), 차단된 포트폴리오(PORTFOLIO_BLOCKED)")
    @GetMapping("/snapshots/{snapshotPublicId}")
    public ResponseEntity<ApiResponse<PortfolioSnapshotDetailInfo>> getSnapshotDetail(
            @Parameter(description = "작가 handle (옛 handle 포함)") @PathVariable
            @Pattern(regexp = HANDLE_PATTERN, message = "handle 형식이 올바르지 않습니다") String handle,
            @Parameter(description = "포트폴리오 공개 코드 (영문 소문자·숫자 6자)") @PathVariable
            @Pattern(regexp = PUBLIC_CODE_PATTERN, message = "포트폴리오 코드 형식이 올바르지 않습니다") String code,
            @Parameter(description = "스냅샷 ID") @PathVariable
            @Pattern(regexp = UUID_PATTERN, message = "스냅샷 ID 형식이 올바르지 않습니다") String snapshotPublicId) {
        return noIndex(ApiResponse.success(
                portfolioService.getSharedSnapshotDetailByCode(handle, code, snapshotPublicId)));
    }

    // 검색엔진 색인 제외(마이페이지_작가-R42) — PortfolioController의 공유 열람과 같다.
    private <T> ResponseEntity<ApiResponse<T>> noIndex(ApiResponse<T> body) {
        return ResponseEntity.ok().header("X-Robots-Tag", "noindex").body(body);
    }

    private int resolveSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    // 공개 GET에서 로그인 여부를 판별한다 — 비로그인이면 null (PortfolioController와 동일 패턴).
    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        return null;
    }
}
