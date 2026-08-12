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
import com.atcrew.portfolio.PortfolioSort;
import com.atcrew.portfolio.PortfolioSummaryInfo;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.portfolio.internal.web.dto.AddPortfolioArtworksRequest;
import com.atcrew.portfolio.internal.web.dto.CreatePortfolioRequest;
import com.atcrew.portfolio.internal.web.dto.UpdatePortfolioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * 공유 링크 공개 열람(`/shared/*`) 2건을 제외하면 전부 본인 전용이다.
 *
 * <p>복제는 원본 정보 조회(`/duplication-source`)만
 * 제공하고, 실제 생성은 프론트가 그 값으로 생성 API를 다시 호출한다(§5.3).
 */
@Tag(name = "포트폴리오", description = """
        작가 페이지·공유 포트폴리오 생성·조회·수정·삭제 API.

        포트폴리오는 두 종류다. 작가 페이지(ARTIST_PAGE)는 회원당 1개로 전 플랜이 쓰며 \
        `GET /api/portfolios/me` 또는 `GET /api/portfolios/selectable` 최초 호출 시 자동 생성되고, \
        제목·공유 슬러그가 없으며 삭제할 수 없다. 공유 포트폴리오(SHARED)는 `POST /api/portfolios`로 만드는 \
        프로 플랜 전용 기능이며 생성 시 공유 슬러그가 발급된다.

        반영 유형(reflectionType)은 생성 시 결정되고 이후 전환할 수 없다. 고정형(SNAPSHOT)은 생성 시점 구성이 \
        얼어붙어 제목 변경·작품 추가/제거가 모두 409로 거부된다.""")
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

    @Operation(summary = "내 포트폴리오 목록", description = """
            로그인한 회원이 소유한 포트폴리오를 커서 페이지네이션으로 조회합니다.

            - 작가 페이지 포트폴리오가 아직 없으면 이 호출 시점에 자동 생성돼 목록에 포함됩니다(회원당 1개).
            - 정렬 기본값은 LATEST(생성일 내림차순)입니다.
            - 응답의 `nextCursor`를 다음 요청의 `cursor`로 그대로 넘깁니다. `hasNext`가 false면 마지막 페이지입니다.
            - 작가 페이지 항목은 `title`·`shareSlug`가 null입니다.
            - 각 항목은 작품 전체가 아니라 커버 썸네일 최대 4개(`coverThumbnails`)만 담습니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "커서 형식 오류(INVALID_CURSOR) 또는 kind·reflectionType·sort 값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me")
    public ApiResponse<CursorPage<PortfolioSummaryInfo>> getMyPortfolios(
            @Parameter(description = "유형 필터 — 미지정 시 전체", example = "SHARED")
            @RequestParam(required = false) PortfolioKind kind,
            @Parameter(description = "반영 유형 필터 — 미지정 시 전체", example = "LIVE")
            @RequestParam(required = false) ReflectionType reflectionType,
            @Parameter(description = "정렬 기준 — 미지정 시 LATEST(최신순)", example = "LATEST")
            @RequestParam(required = false) PortfolioSort sort,
            @Parameter(description = "커서 — 직전 응답의 nextCursor(정렬 기준 시각의 epochMilli). "
                    + "첫 페이지는 생략합니다", example = "1786496899160")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 최대 50. 1 미만이거나 생략하면 20, 50 초과면 50으로 보정됩니다",
                    example = "20")
            @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getMyPortfolios(
                memberId, kind, reflectionType, sort, cursor, resolveSize(size)));
    }

    @Operation(summary = "선택 가능한 포트폴리오 목록", description = """
            작품 업로드·수정 화면에서 "이 작품을 담을 포트폴리오" 선택지로 쓰는 목록입니다.

            - 작가 페이지와 최신 반영형(LIVE)만 포함되며 고정형(SNAPSHOT)은 제외됩니다.
            - 작가 페이지가 아직 없으면 이 호출 시점에 자동 생성되며, 항상 목록 맨 앞에 옵니다.
            - 페이지네이션 없이 본인 소유 전부를 내려주고 플랜 게이팅도 하지 않습니다 \
            (스타터에서 선택 불가 처리는 프론트가 판단합니다).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/selectable")
    public ApiResponse<List<PortfolioSelectableInfo>> getSelectablePortfolios() {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getSelectablePortfolios(memberId));
    }

    @Operation(summary = "공유 포트폴리오 생성", description = """
            공유 포트폴리오(kind=SHARED)를 생성합니다. **프로 플랜 전용**이라 스타터 계정은 403 PRO_PLAN_REQUIRED입니다.
            작가 페이지는 이 API로 만들 수 없습니다(목록 조회 시 자동 생성).

            - `reflectionType`은 생성 시 결정되고 이후 전환할 수 없습니다. SNAPSHOT으로 만들면 그 시점의 작품 표시 \
            정보와 작성자 이름이 복사돼 원본 변경에 영향받지 않고, 이후 수정·작품 추가/제거가 모두 409로 거부됩니다.
            - 응답의 `shareSlug`로 공유 URL을 구성합니다.
            - `artworkIds`는 본인 소유의 삭제되지 않은 작품이어야 하며(아니면 404 ARTWORK_NOT_FOUND), \
            요청 순서와 무관하게 작품 업로드순(오래된순)으로 정렬됩니다. 0개로도 생성할 수 있습니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "입력값 유효성 오류 또는 제목이 공백(INVALID_PORTFOLIO_TITLE)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "프로 플랜 아님(PRO_PLAN_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "담을 작품 없음 — 존재하지 않거나 본인 소유가 아니거나 삭제된 작품(ARTWORK_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "서버 내부 오류 — 공유 링크 슬러그 생성 실패 포함(SLUG_GENERATION_FAILED)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PortfolioInfo> createPortfolio(@RequestBody @Valid CreatePortfolioRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.createShared(
                memberId, request.title(), request.reflectionType(), request.artworkIds()));
    }

    @Operation(summary = "포트폴리오 상세 조회", description = """
            본인 소유 포트폴리오의 상세와 담긴 작품 전체를 조회합니다. 타인 소유는 403입니다.

            - 최신 반영형·작가 페이지는 원본 작품을 조회 시점에 읽으므로 원본이 삭제됐거나 휴지통에 있으면 \
            `artworks`에서 빠집니다(`itemCount`는 그대로라 배열 길이보다 클 수 있습니다).
            - 고정형은 생성 시점에 복사해 둔 값을 그대로 내려줍니다.
            - 작가 페이지는 `title`·`shareSlug`가 null입니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "포트폴리오 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND)")
    })
    @GetMapping("/{portfolioId}")
    public ApiResponse<PortfolioInfo> getPortfolio(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getPortfolio(memberId, portfolioId));
    }

    @Operation(summary = "복제 원본 조회", description = """
            복제 시작 화면을 미리 채우는 값을 조회합니다. **이 호출만으로는 복제본이 만들어지지 않습니다** — \
            응답의 `defaultTitle`·`selectedArtworkIds`를 그대로 포트폴리오 생성 API(`POST /api/portfolios`)에 \
            넘겨 복제본을 만듭니다(유형·반영 유형은 상속되지 않고 사용자가 다시 고릅니다).

            - 삭제·휴지통·비공개(PRIVATE) 작품은 자동 선택에서 빠지고 그 개수가 `excludedCount`로 내려갑니다 \
            (링크공개 LINK_ONLY는 빠지지 않습니다).
            - 자동 선택이 0개여도 정상 응답이며 그대로 복제를 진행할 수 있습니다.
            - 고정형 원본도 스냅샷이 아니라 원본 작품의 현재 상태로 판정합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "포트폴리오 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND)")
    })
    @GetMapping("/{portfolioId}/duplication-source")
    public ApiResponse<PortfolioDuplicationSourceInfo> getDuplicationSource(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.getDuplicationSource(memberId, portfolioId));
    }

    @Operation(summary = "포트폴리오 수정", description = """
            제목과 구성 작품을 부분 수정합니다. null인 필드는 변경하지 않으며, 수정된 상세를 응답으로 돌려줍니다.

            유형별 제약:
            - 고정형(SNAPSHOT): 어떤 수정도 불가 — 항상 409 SNAPSHOT_PORTFOLIO_IMMUTABLE.
            - 작가 페이지(ARTIST_PAGE): 제목 변경 불가 — `title`을 보내면 400 ARTIST_PAGE_TITLE_IMMUTABLE. \
            구성 작품 수정은 플랜과 무관하게 가능합니다.
            - 공유(SHARED): 프로 플랜 전용 — 스타터로 다운그레이드되면 403 PRO_PLAN_REQUIRED.

            `artworkIds`는 부분 수정이 아니라 전체 교체입니다(빈 배열이면 전부 비웁니다). 순서는 요청 순서와 \
            무관하게 작품 업로드순으로 정렬됩니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "입력값 유효성 오류, 작가 페이지 제목 변경 시도(ARTIST_PAGE_TITLE_IMMUTABLE), "
                            + "제목이 공백(INVALID_PORTFOLIO_TITLE)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED) 또는 프로 플랜 아님(PRO_PLAN_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND) 또는 담을 작품 없음(ARTWORK_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "고정형 포트폴리오라 수정 불가(SNAPSHOT_PORTFOLIO_IMMUTABLE)")
    })
    @PatchMapping("/{portfolioId}")
    public ApiResponse<PortfolioInfo> updatePortfolio(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @RequestBody @Valid UpdatePortfolioRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(portfolioService.updatePortfolio(
                memberId, portfolioId, request.title(), request.artworkIds()));
    }

    @Operation(summary = "포트폴리오 삭제", description = """
            공유 포트폴리오를 삭제합니다. 응답 본문이 없습니다(204).

            - 작가 페이지는 삭제할 수 없습니다 — 409 ARTIST_PAGE_NOT_DELETABLE.
            - 삭제에는 프로 게이팅이 없습니다 — 스타터로 다운그레이드된 뒤에도 정리 목적의 삭제는 허용됩니다.
            - 담긴 작품 자체는 삭제되지 않습니다. 삭제 후 해당 공유 링크는 404가 됩니다.
            - 고정형도 삭제할 수 있습니다(수정만 막힙니다).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "포트폴리오 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "작가 페이지는 삭제 불가(ARTIST_PAGE_NOT_DELETABLE)")
    })
    @DeleteMapping("/{portfolioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePortfolio(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.deletePortfolio(memberId, portfolioId);
    }

    @Operation(summary = "포트폴리오에 작품 추가", description = """
            기존 구성 뒤에 작품을 추가한 뒤 전체를 작품 업로드순(오래된순)으로 다시 정렬합니다. \
            응답 본문이 없으므로(204) 갱신된 구성은 상세 조회로 확인합니다.

            - 이미 담긴 작품을 다시 보내도 오류가 아니라 무시됩니다(중복 생성 없음).
            - 고정형(SNAPSHOT)에는 추가할 수 없습니다 — 409 SNAPSHOT_PORTFOLIO_IMMUTABLE.
            - 공유 포트폴리오 수정은 프로 플랜 전용입니다 — 403 PRO_PLAN_REQUIRED. 작가 페이지는 플랜과 무관합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "입력값 유효성 오류(artworkIds 누락·빈 배열·100개 초과) 또는 포트폴리오 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED) 또는 프로 플랜 아님(PRO_PLAN_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND) 또는 담을 작품 없음(ARTWORK_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "고정형 포트폴리오라 수정 불가(SNAPSHOT_PORTFOLIO_IMMUTABLE)")
    })
    @PostMapping("/{portfolioId}/artworks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addArtworks(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @RequestBody @Valid AddPortfolioArtworksRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.addArtworks(memberId, portfolioId, request.artworkIds());
    }

    @Operation(summary = "포트폴리오에서 작품 제거", description = """
            포트폴리오 구성에서 작품 1건을 뺍니다. 작품 자체는 삭제되지 않고 남은 구성의 상대 순서도 유지됩니다. \
            응답 본문이 없습니다(204).

            - 해당 포트폴리오에 담겨 있지 않은 작품이면 404 ARTWORK_NOT_FOUND입니다(이미 뺀 작품을 또 빼는 경우 포함).
            - 고정형(SNAPSHOT)에서는 제거할 수 없습니다 — 409 SNAPSHOT_PORTFOLIO_IMMUTABLE.
            - 공유 포트폴리오 수정은 프로 플랜 전용입니다 — 403 PRO_PLAN_REQUIRED. 작가 페이지는 플랜과 무관합니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제거 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "포트폴리오 ID 또는 작품 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED) 또는 프로 플랜 아님(PRO_PLAN_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "존재하지 않는 포트폴리오(PORTFOLIO_NOT_FOUND) 또는 "
                            + "포트폴리오에 담겨 있지 않은 작품(ARTWORK_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "고정형 포트폴리오라 수정 불가(SNAPSHOT_PORTFOLIO_IMMUTABLE)")
    })
    @DeleteMapping("/{portfolioId}/artworks/{artworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArtwork(
            @Parameter(description = "포트폴리오 ID (UUID)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "포트폴리오 ID 형식이 올바르지 않습니다") String portfolioId,
            @Parameter(description = "제거할 작품 ID (UUID)", example = "019ff382-bd4a-7045-80ac-7430bd0832c7")
            @PathVariable @Pattern(regexp = UUID_PATTERN, message = "작품 ID 형식이 올바르지 않습니다") String artworkId) {
        String memberId = securityUtils.getCurrentMemberId();
        portfolioService.removeArtwork(memberId, portfolioId, artworkId);
    }

    @Operation(summary = "공유 포트폴리오 열람 (공개)", description = """
            공유 링크로 여는 포트폴리오 헤더 정보입니다. **인증이 필요 없는 공개 엔드포인트**로 \
            비로그인 상태에서도 호출할 수 있습니다(Authorization 헤더를 보내도 무시됩니다).

            - `identifier`는 공유 슬러그 또는 작가 handle입니다. 슬러그를 먼저 찾고, 없으면 handle로 \
            해당 작가의 작가 페이지 포트폴리오를 찾습니다.
            - 소유자 식별자(회원 ID·핸들)와 공유 슬러그는 응답에 담지 않습니다.
            - 담긴 작품은 `/api/portfolios/shared/{identifier}/artworks`로 따로 조회합니다.
            - 소유자가 탈퇴하면 슬러그 열람은 410 PORTFOLIO_BLOCKED가 되고, handle은 해제되므로 404가 됩니다.
            - 검색엔진 색인 제외를 위해 `X-Robots-Tag: noindex` 헤더를 함께 내려줍니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    headers = @Header(name = "X-Robots-Tag", description = "검색엔진 색인 제외 지시 — 항상 noindex",
                            schema = @Schema(type = "string", example = "noindex"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "공유 식별자 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "해당 슬러그·handle의 포트폴리오 없음(PORTFOLIO_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
                    description = "소유자 탈퇴 등으로 더 이상 열람할 수 없음(PORTFOLIO_BLOCKED)")
    })
    @GetMapping("/shared/{identifier}")
    public ResponseEntity<ApiResponse<PortfolioSharedInfo>> getSharedPortfolio(
            @Parameter(description = "공유 슬러그(base64url 22자) 또는 작가 handle", example = "Ry-yvvhvG1lm-3DqjG64nQ")
            @PathVariable @Pattern(regexp = SHARED_IDENTIFIER_PATTERN, message = "공유 식별자 형식이 올바르지 않습니다") String identifier) {
        return noIndex(ApiResponse.success(portfolioService.getSharedPortfolio(identifier)));
    }

    @Operation(summary = "공유 포트폴리오 작품 목록 (공개)", description = """
            공유 링크로 여는 포트폴리오의 작품 목록입니다. **인증이 필요 없는 공개 엔드포인트**로 \
            비로그인 상태에서도 호출할 수 있습니다.

            - `identifier` 해석 규칙은 헤더 조회 API와 같습니다(슬러그 우선, 없으면 작가 handle).
            - 정렬은 작품 업로드순(오래된순) 고정이며, 커서는 직전 페이지 마지막 항목의 순번(ordinal)입니다. \
            응답의 `nextCursor`를 그대로 다음 요청의 `cursor`로 넘깁니다.
            - 최신 반영형·작가 페이지는 원본 작품을 조회 시점에 읽으므로 삭제됐거나 휴지통에 있는 작품이 \
            조용히 빠집니다 — `items` 길이가 `size`보다 작을 수 있으니 다음 페이지 존재 여부는 `hasNext`로 판단합니다.
            - 고정형은 생성 시점에 복사해 둔 값만 읽으므로 원본 변경에 영향받지 않습니다.
            - 검색엔진 색인 제외를 위해 `X-Robots-Tag: noindex` 헤더를 함께 내려줍니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    headers = @Header(name = "X-Robots-Tag", description = "검색엔진 색인 제외 지시 — 항상 noindex",
                            schema = @Schema(type = "string", example = "noindex"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "공유 식별자 형식 오류 또는 커서 형식 오류(INVALID_CURSOR)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "해당 슬러그·handle의 포트폴리오 없음(PORTFOLIO_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410",
                    description = "소유자 탈퇴 등으로 더 이상 열람할 수 없음(PORTFOLIO_BLOCKED)")
    })
    @GetMapping("/shared/{identifier}/artworks")
    public ResponseEntity<ApiResponse<CursorPage<PortfolioArtworkCardInfo>>> getSharedPortfolioArtworks(
            @Parameter(description = "공유 슬러그(base64url 22자) 또는 작가 handle", example = "Ry-yvvhvG1lm-3DqjG64nQ")
            @PathVariable @Pattern(regexp = SHARED_IDENTIFIER_PATTERN, message = "공유 식별자 형식이 올바르지 않습니다") String identifier,
            @Parameter(description = "커서 — 직전 응답의 nextCursor(마지막 항목의 순번). 첫 페이지는 생략합니다",
                    example = "1")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 최대 50. 1 미만이거나 생략하면 20, 50 초과면 50으로 보정됩니다",
                    example = "20")
            @RequestParam(required = false) Integer size) {
        return noIndex(ApiResponse.success(
                portfolioService.getSharedPortfolioArtworks(identifier, cursor, resolveSize(size))));
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
