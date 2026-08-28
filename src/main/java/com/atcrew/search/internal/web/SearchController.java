package com.atcrew.search.internal.web;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.MaterialTarget;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.search.PostType;
import com.atcrew.search.SearchPage;
import com.atcrew.search.SearchQuery;
import com.atcrew.search.SearchResultItem;
import com.atcrew.search.SearchService;
import com.atcrew.search.SearchSort;
import com.atcrew.search.internal.exception.SearchErrorCode;
import com.atcrew.search.internal.exception.SearchException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검색 API. 피그마 UI개편_검색(5154:41768) 기준 — 텍스트 검색 + 다중선택 chip 필터(작품 분야·창작 유형·
 * 연령대·담당 업무·장르·소재 대상) + 게시글 유형(포트폴리오·구인글·구직글·팀원모집글).
 *
 * <p>구인글/구직글/팀원모집글은 recruit 모듈에서 조회하며, 포트폴리오와 함께 요청하면 최신순으로 병합해 반환합니다.
 */
@Tag(name = "검색", description = "태그 기반 검색 API — 포트폴리오·구인글·구직글·팀원모집글 통합 검색")
@RestController
@RequestMapping("/api/search")
class SearchController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final SearchService searchService;
    private final MemberService memberService;

    SearchController(SearchService searchService, MemberService memberService) {
        this.searchService = searchService;
        this.memberService = memberService;
    }

    @Operation(summary = "검색", description =
            "검색어와 필터가 모두 비어 있으면 빈 결과를 반환합니다(최초 진입 상태 — 결과 목록 미노출). " +
            "각 필터 축은 다중선택이며 축 내부는 OR, 축 간에는 AND로 결합됩니다. " +
            "포트폴리오와 구인글/구직글/팀원모집글을 함께 요청하면(예: postTypes 미지정) 관련도 비교가 불가능해 " +
            "최신순으로 병합됩니다 — 이때 sort=OLDEST를 요청하면 조용히 최신순으로 바뀌는 대신 400을 반환합니다. " +
            "작품 분야·창작 유형·연령대·소재 대상 필터는 포트폴리오에만 존재하는 속성이라, 해당 필터가 걸리면 " +
            "구인글/구직글/팀원모집글은 결과에서 제외됩니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "INVALID_CURSOR / INVALID_SIZE / UNSUPPORTED_SORT_FOR_MERGED_SEARCH")
    @GetMapping
    public ApiResponse<SearchPage<SearchResultItem>> search(
            @Parameter(description = "검색어") @RequestParam(required = false) String q,
            @Parameter(description = "게시글 유형 필터 (다중선택)") @RequestParam(required = false) List<PostType> postTypes,
            @Parameter(description = "작품 분야 필터 (다중선택)") @RequestParam(required = false) List<ArtworkField> artworkFields,
            @Parameter(description = "창작 유형 필터 (다중선택)") @RequestParam(required = false) List<CreativeType> creativeTypes,
            @Parameter(description = "연령대 필터 (다중선택)") @RequestParam(required = false) List<AgeRating> ageRatings,
            @Parameter(description = "담당 업무 필터 (다중선택)") @RequestParam(required = false) List<ArtworkRole> roles,
            @Parameter(description = "장르 필터 (다중선택)") @RequestParam(required = false) List<Genre> genres,
            @Parameter(description = "소재 대상 필터 (다중선택)") @RequestParam(required = false) List<MaterialTarget> materialTargets,
            @Parameter(description = "정렬 기준 (RELEVANCE·LATEST·OLDEST, 기본: 검색어 있으면 RELEVANCE, 없으면 LATEST). " +
                    "OLDEST는 포트폴리오와 구인글/구직글/팀원모집글을 함께 조회하는 통합검색(postTypes 미지정 포함)에서는 " +
                    "지원하지 않으며 400(UNSUPPORTED_SORT_FOR_MERGED_SEARCH)을 반환합니다.")
            @RequestParam(required = false) SearchSort sort,
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String viewerMemberId = getOptionalMemberId();
        return ApiResponse.success(searchService.search(new SearchQuery(
                q, postTypes, artworkFields, creativeTypes, ageRatings, roles, genres, materialTargets,
                viewerLanguages(viewerMemberId), viewerMemberId, memberService.isAdultContentVisible(viewerMemberId),
                sort, cursor, resolveSize(size))));
    }

    // 언어 세그먼트 필터 기준(로그인-R16). 비로그인은 빈 목록 → 필터 미적용(전체 노출).
    private List<Language> viewerLanguages(String viewerMemberId) {
        return viewerMemberId != null ? memberService.findPostLanguages(viewerMemberId) : List.of();
    }

    // 공개 GET에서 로그인 여부를 판별한다 — 비로그인이면 null (member/company 모듈과 동일 패턴).
    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return principal.memberId();
        }
        return null;
    }

    private int resolveSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        if (size < 1) throw new SearchException(SearchErrorCode.INVALID_SIZE);
        return Math.min(size, MAX_SIZE);
    }
}
