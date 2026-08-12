package com.atcrew.search.internal.web;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.common.response.ApiResponse;
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

    SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "검색", description =
            "검색어와 필터가 모두 비어 있으면 빈 결과(items=[], totalCount=0)를 반환합니다(최초 진입 상태 — 결과 목록 미노출). " +
            "각 필터 축은 다중선택이며 축 내부는 OR, 축 간에는 AND로 결합됩니다. " +
            "다중값 파라미터는 콤마 구분(`postTypes=PORTFOLIO,JOB_POSTING`) 또는 파라미터 반복" +
            "(`postTypes=PORTFOLIO&postTypes=JOB_POSTING`) 두 방식 모두 지원합니다. " +
            "포트폴리오와 구인글/구직글/팀원모집글을 함께 요청하면 정렬 요청과 무관하게 최신순으로 병합됩니다. " +
            "작품 분야·창작 유형·연령대·소재 대상 필터는 포트폴리오에만 존재하는 속성이라, 해당 필터가 걸리면 " +
            "구인글/구직글/팀원모집글은 결과에서 제외됩니다. " +
            "size가 1 미만이면 400 INVALID_SIZE, 커서 형식이 어긋나면 400 INVALID_CURSOR, " +
            "열거형 파라미터에 정의되지 않은 값을 보내면 400을 반환합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponse<SearchPage<SearchResultItem>> search(
            @Parameter(description = "검색어 — 포트폴리오는 제목(가중치 높음)·설명·작가명·태그를, "
                    + "구인글/구직글/팀원모집글은 제목을 대상으로 매칭합니다. 공백만 입력하면 미입력과 같게 처리됩니다",
                    example = "판타지") @RequestParam(required = false) String q,
            @Parameter(description = "게시글 유형 필터 — 콤마 구분 다중값. 미지정 시 전체 유형 검색",
                    example = "PORTFOLIO,JOB_POSTING") @RequestParam(required = false) List<PostType> postTypes,
            @Parameter(description = "작품 분야 필터 — 콤마 구분 다중값(ILLUSTRATION·WEBTOON·PRINT_COMIC·ANIMATION·ETC). "
                    + "포트폴리오 전용 속성이라 값을 지정하면 구인글/구직글/팀원모집글은 결과에서 제외됩니다",
                    example = "ILLUSTRATION,WEBTOON") @RequestParam(required = false) List<ArtworkField> artworkFields,
            @Parameter(description = "창작 유형 필터 — 콤마 구분 다중값(ORIGINAL·SECONDARY·FAN_ART·OC·COMMISSION). "
                    + "포트폴리오 전용 속성이라 값을 지정하면 구인글/구직글/팀원모집글은 결과에서 제외됩니다",
                    example = "ORIGINAL,FAN_ART") @RequestParam(required = false) List<CreativeType> creativeTypes,
            @Parameter(description = "연령대 필터 — 콤마 구분 다중값(ALL 전체연령가·R18·G18). "
                    + "포트폴리오 전용 속성이라 값을 지정하면 구인글/구직글/팀원모집글은 결과에서 제외됩니다",
                    example = "ALL") @RequestParam(required = false) List<AgeRating> ageRatings,
            @Parameter(description = "담당 업무 필터 — 콤마 구분 다중값(LINEART·COLORING·BACKGROUND 등 ArtworkRole 상수). "
                    + "포트폴리오와 구인글/구직글/팀원모집글 양쪽에 적용됩니다",
                    example = "LINEART,COLORING") @RequestParam(required = false) List<ArtworkRole> roles,
            @Parameter(description = "장르 필터 — 콤마 구분 다중값. 정본 목록이 확정되기 전이라 자유 문자열이며 정확히 일치해야 합니다. "
                    + "값에 콤마가 들어가면 파라미터 반복 방식을 쓰세요. 포트폴리오와 구인글/구직글/팀원모집글 양쪽에 적용됩니다",
                    example = "판타지,로맨스") @RequestParam(required = false) List<String> genres,
            @Parameter(description = "소재 대상 필터 — 콤마 구분 다중값. 자유 문자열이며 정확히 일치해야 합니다. "
                    + "포트폴리오 전용 속성이라 값을 지정하면 구인글/구직글/팀원모집글은 결과에서 제외됩니다",
                    example = "남성,여성") @RequestParam(required = false) List<String> materialTargets,
            @Parameter(description = "정렬 기준 — 미지정 시 검색어가 있으면 RELEVANCE, 없으면 LATEST. "
                    + "포트폴리오와 구인글/구직글/팀원모집글이 함께 조회되면 이 값과 무관하게 최신순으로 병합됩니다",
                    example = "LATEST") @RequestParam(required = false) SearchSort sort,
            @Parameter(description = "커서 — 직전 응답의 nextCursor 값을 그대로 전달합니다(base64url로 인코딩된 불투명 문자열). "
                    + "첫 페이지는 생략합니다. 커서 내부 구조는 검색 대상(포트폴리오 단독 / 포트폴리오+구인글 등 병합)과 정렬에 따라 "
                    + "달라지므로, 검색 조건이나 정렬을 바꾸면 커서를 버리고 첫 페이지부터 다시 조회해야 합니다. "
                    + "형식이 맞지 않으면 400 INVALID_CURSOR를 반환합니다")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 50 초과 시 50으로 잘립니다. 1 미만이면 400 INVALID_SIZE",
                    example = "20") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(searchService.search(new SearchQuery(
                q, postTypes, artworkFields, creativeTypes, ageRatings, roles, genres, materialTargets,
                sort, cursor, resolveSize(size))));
    }

    private int resolveSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        if (size < 1) throw new SearchException(SearchErrorCode.INVALID_SIZE);
        return Math.min(size, MAX_SIZE);
    }
}
