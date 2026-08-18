package com.atcrew.portfolio.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.portfolio.PortfolioArtworkCardInfo;
import com.atcrew.portfolio.PortfolioDuplicationSourceInfo;
import com.atcrew.portfolio.PortfolioInfo;
import com.atcrew.portfolio.PortfolioKind;
import com.atcrew.portfolio.PortfolioSelectableInfo;
import com.atcrew.portfolio.PortfolioSharedInfo;
import com.atcrew.portfolio.PortfolioSnapshotDetailInfo;
import com.atcrew.portfolio.PortfolioSort;
import com.atcrew.portfolio.PortfolioSummaryInfo;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.portfolio.internal.web.dto.AddPortfolioArtworksRequest;
import com.atcrew.portfolio.internal.web.dto.CreatePortfolioRequest;
import com.atcrew.portfolio.internal.web.dto.UpdatePortfolioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 포트폴리오 CRUD API (docs/design/portfolio-module-design.md §4).
 * 공유 링크 공개 열람(`/shared/*`) 3건을 제외하면 전부 본인 전용이다.
 *
 * <p>복제는 원본 정보 조회(`/duplication-source`)만
 * 제공하고, 실제 생성은 프론트가 그 값으로 생성 API를 다시 호출한다(§5.3).
 */
@Tag(name = "포트폴리오", description = "작가 페이지·공유 포트폴리오 생성·조회·수정·삭제 API")
@Validated
@RestController
@RequestMapping("/api/portfolios")
class PortfolioController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    // 공유 슬러그(base64url 22자)와 작가 handle(3~30자)이 한 경로를 공유하므로 둘 다 통과하는 느슨한 패턴을 쓴다(§2.6).
    private static final String SHARED_IDENTIFIER_PATTERN = "^[A-Za-z0-9_-]{3,64}$";

    private final PortfolioServiceImpl portfolioService;
    private final SecurityUtils securityUtils;

    PortfolioController(PortfolioServiceImpl portfolioService, SecurityUtils securityUtils) {
        this.portfolioService = portfolioService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "내 포트폴리오 목록",
            description = "작가 페이지 포트폴리오가 없으면 이 시점에 생성됩니다. 정렬 기본값은 최신순입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me")
    public ApiResponse<CursorPage<PortfolioSummaryInfo>> getMyPortfolios(
            @Parameter(description = "유형 필터 (ARTIST_PAGE/SHARED)") @RequestParam(required = false) PortfolioKind kind,
            @Parameter(description = "반영 유형 필터 (LIVE/SNAPSHOT)") @RequestParam(required = false) ReflectionType reflectionType,
            @Parameter(description = "정렬 (OLDEST/LATEST/UPDATED, 기본 LATEST). UPDATED는 [수정하기]로 저장한 시각 기준입니다")
            @RequestParam(required = false) PortfolioSort sort,
            @Parameter(description = "커서 (직전 페이지 마지막 항목의 nextCursor 값을 그대로 전달)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getMyPortfolios(
                memberId, kind, reflectionType, sort, cursor, resolveSize(size)));
    }

    @Operation(summary = "선택 가능한 포트폴리오 목록",
            description = "작품 업로드·수정 화면용. 작가 페이지와 최신 반영형만 포함되며 고정형은 제외됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/selectable")
    public ApiResponse<List<PortfolioSelectableInfo>> getSelectablePortfolios() {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getSelectablePortfolios(memberId));
    }

    @Operation(summary = "공유 포트폴리오 생성", description = "프로 플랜 전용입니다. 응답의 shareSlug로 공유 URL을 구성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortfolioInfo> createPortfolio(@RequestBody @Valid CreatePortfolioRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.createShared(
                memberId, request.title(), request.reflectionType(), request.artworkIds()));
    }

    @Operation(summary = "포트폴리오 상세 조회", description = "본인 소유 포트폴리오만 조회할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioInfo> getPortfolio(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getPortfolio(memberId, portfolioId));
    }

    @Operation(summary = "복제 원본 조회",
            description = "복제 시작 화면용. 삭제됐거나 완전 비공개(비공개 + 어느 라이브 포트폴리오에도 미포함)인 작품은 "
                    + "자동 선택에서 빠지며, 선택된 작품이 0개여도 복제를 진행할 수 있습니다. "
                    + "실제 복제본은 이 응답 값으로 포트폴리오 생성 API를 호출해 만듭니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{portfolioId}/duplication-source")
    public ApiResponse<PortfolioDuplicationSourceInfo> getDuplicationSource(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getDuplicationSource(memberId, portfolioId));
    }

    @Operation(summary = "포트폴리오 수정",
            description = "고정형은 수정할 수 없고, 작가 페이지는 제목을 변경할 수 없습니다. 공유 포트폴리오 수정은 프로 전용입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PatchMapping("/{portfolioId}")
    public ApiResponse<PortfolioInfo> updatePortfolio(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @RequestBody @Valid UpdatePortfolioRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.updatePortfolio(
                memberId, portfolioId, request.title(), request.artworkIds()));
    }

    @Operation(summary = "포트폴리오 삭제", description = "작가 페이지 포트폴리오는 삭제할 수 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/{portfolioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePortfolio(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.deletePortfolio(memberId, portfolioId);
    }

    @Operation(summary = "포트폴리오에 작품 추가", description = "고정형에는 추가할 수 없습니다. 공유 포트폴리오는 프로 전용입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "추가 성공")
    @PostMapping("/{portfolioId}/artworks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addArtworks(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @RequestBody @Valid AddPortfolioArtworksRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.addArtworks(memberId, portfolioId, request.artworkIds());
    }

    @Operation(summary = "포트폴리오에서 작품 제거", description = "고정형에서는 제거할 수 없습니다. 공유 포트폴리오는 프로 전용입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제거 성공")
    @DeleteMapping("/{portfolioId}/artworks/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArtwork(
            @Parameter(description = "포트폴리오 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @Parameter(description = "작품 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "작품 ID 형식이 올바르지 않습니다") String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.removeArtwork(memberId, portfolioId, artworkId);
    }

    @Operation(summary = "공유 포트폴리오 열람",
            description = "인증이 필요 없습니다. 식별자는 공유 슬러그 또는 작가 handle이며 슬러그를 먼저 해석합니다. "
                    + "검색엔진 색인 제외를 위해 X-Robots-Tag: noindex 헤더를 함께 내려줍니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/shared/{identifier}")
    public ResponseEntity<ApiResponse<PortfolioSharedInfo>> getSharedPortfolio(
            @Parameter(description = "공유 슬러그 또는 작가 handle") @PathVariable @Pattern(regexp = SHARED_IDENTIFIER_PATTERN, message = "공유 식별자 형식이 올바르지 않습니다") String identifier) {
        return noIndex(ApiResponse.success(portfolioService.getSharedPortfolio(identifier)));
    }

    @Operation(summary = "공유 포트폴리오 작품 목록",
            description = "인증이 필요 없습니다. 작품 순서는 업로드순(오래된순) 고정이며 커서는 마지막 항목의 순번입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/shared/{identifier}/artworks")
    public ResponseEntity<ApiResponse<CursorPage<PortfolioArtworkCardInfo>>> getSharedPortfolioArtworks(
            @Parameter(description = "공유 슬러그 또는 작가 handle") @PathVariable @Pattern(regexp = SHARED_IDENTIFIER_PATTERN, message = "공유 식별자 형식이 올바르지 않습니다") String identifier,
            @Parameter(description = "커서 (직전 페이지 마지막 항목의 순번)") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        return noIndex(ApiResponse.success(
                portfolioService.getSharedPortfolioArtworks(identifier, cursor, resolveSize(size))));
    }

    @Operation(summary = "고정형 스냅샷 상세 열람",
            description = "인증이 필요 없습니다. 고정형(SNAPSHOT) 포트폴리오 안에서만 열리는 독립 자원이며, "
                    + "생성 시점에 얼린 값만 내려줍니다. 원본 작품 ID나 원본으로 이동하는 링크는 제공하지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/shared/{identifier}/snapshots/{snapshotId}")
    public ResponseEntity<ApiResponse<PortfolioSnapshotDetailInfo>> getSharedSnapshotDetail(
            @Parameter(description = "공유 슬러그 또는 작가 handle") @PathVariable @Pattern(regexp = SHARED_IDENTIFIER_PATTERN, message = "공유 식별자 형식이 올바르지 않습니다") String identifier,
            @Parameter(description = "스냅샷 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "스냅샷 ID 형식이 올바르지 않습니다") String snapshotId) {
        return noIndex(ApiResponse.success(portfolioService.getSharedSnapshotDetail(identifier, snapshotId)));
    }

    // 검색엔진 색인 제외(마이페이지_작가-R42) — 페이지 <meta name="robots">는 프론트 SSR이 담당하고 서버는 헤더로 보조한다.
    private <T> ResponseEntity<ApiResponse<T>> noIndex(ApiResponse<T> body) {
        return ResponseEntity.ok().header("X-Robots-Tag", "noindex").body(body);
    }

    private int resolveSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
