package com.atcrew.community.internal.web;

import com.atcrew.community.internal.exception.CommunityErrorCode;
import com.atcrew.community.internal.exception.CommunityException;
import com.atcrew.community.internal.web.dto.CommunityPage;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSort;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.OffsetPage;
import com.atcrew.member.ActivityField;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.Language;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.ProfileSort;
import com.atcrew.member.SearchProfilesCommand;
import com.atcrew.recruit.CommunityJobPostingCardInfo;
import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.RecruitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 커뮤니티 화면 진입점 API. 피그마 UI개편_커뮤니티 기준 4개 탭(포트폴리오·작가 프로필·구인글·팀원모집글)을
 * 각각 다른 모듈에 위임하는 파사드 — 탭들은 하나의 통합 정렬 피드가 아니라 독립된 목록이다
 * (docs/design/community-module-design.md §1.2).
 */
@Tag(name = "커뮤니티", description = "커뮤니티 피드 API — 포트폴리오·작가 찾아보기·구인글·팀원모집글 탭")
@RestController
@RequestMapping("/api/community")
class CommunityController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    /** 오프셋 조회 상한 — OFFSET은 건너뛸 행을 실제로 읽으므로 깊은 페이지를 막는다(설계 §6). */
    private static final int MAX_OFFSET = 10_000;

    private final ArtworkService artworkService;
    private final MemberService memberService;
    private final RecruitService recruitService;

    CommunityController(ArtworkService artworkService, MemberService memberService, RecruitService recruitService) {
        this.artworkService = artworkService;
        this.memberService = memberService;
        this.recruitService = recruitService;
    }

    @Operation(summary = "포트폴리오 탭 — 커뮤니티 작품 목록", description =
            "공개 작품을 정렬 기준에 따라 조회합니다. 인증 불필요. "
            + "totalCount는 현재 필터·뷰어 조건(언어 세그먼트·성인 콘텐츠 표시 설정)에 맞는 전체 작품 수입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "page가 1 미만이거나 조회 상한(page × size > 10000) 초과(INVALID_PAGE) / size가 음수(INVALID_SIZE)")
    @GetMapping("/artworks")
    public ApiResponse<CommunityPage<ArtworkSummaryInfo>> getArtworks(
            @Parameter(description = "작품 분야 필터") @RequestParam(required = false) ArtworkField artworkField,
            @Parameter(description = "연령 등급 필터 (기본 ALL)") @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "정렬 기준 (LATEST·OLDEST·VIEW_COUNT·BOOKMARK_COUNT, 기본 LATEST)")
            @RequestParam(required = false, defaultValue = "LATEST") ArtworkSort sort,
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        int resolvedSize = resolveSize(size);
        int resolvedPage = resolvePage(page, resolvedSize);
        if (resolvedSize == 0) {
            return ApiResponse.success(emptyPage(resolvedPage));
        }
        String viewerMemberId = getOptionalMemberId();
        List<Language> viewerLanguages = viewerLanguages(viewerMemberId);
        boolean adultContentVisible = memberService.isAdultContentVisible(viewerMemberId);
        return ApiResponse.success(CommunityPage.of(
                artworkService.getCommunityArtworks(artworkField, ageRating, viewerLanguages, sort,
                        resolvedPage, resolvedSize, viewerMemberId, adultContentVisible),
                resolvedPage, resolvedSize));
    }

    @Operation(summary = "이번 주 가장 핫한 작품", description =
            "최근 7일(168시간) 기간 조회수 순으로 최대 6개를 조회합니다. 인증 불필요. 기간 조회수는 매시 정각 갱신됩니다. "
            + "노출 조건은 포트폴리오 탭 목록과 같고(공개·언어·성인 콘텐츠 설정), 기간 조회수가 같으면 북마크 수 → 최신 등록순입니다. "
            + "기간 조회수가 있는 작품이 6개 미만이면 조회수가 없는 작품으로 같은 순서에 따라 채웁니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/artworks/hot")
    public ApiResponse<List<ArtworkSummaryInfo>> getHotArtworks() {
        String viewerMemberId = getOptionalMemberId();
        return ApiResponse.success(artworkService.getHotArtworks(
                viewerLanguages(viewerMemberId), viewerMemberId, memberService.isAdultContentVisible(viewerMemberId)));
    }

    @Operation(summary = "작가 프로필 탭 — 작가 찾아보기", description =
            "구인 가능 상태(신규 작업 가능·협의 가능)인 창작자 프로필 목록을 조회합니다. 인증 불필요. "
            + "노출 대상 항목(사용자 이름·활동 분야·활동 경력·희망 담당 업무·희망 장르·희망 채용 형태·연락처)이 "
            + "비어 있는 프로필은 제외됩니다. totalCount는 현재 필터와 뷰어의 언어 세그먼트(로그인 회원의 주 사용 언어)에 "
            + "맞는 전체 작가 수입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "page가 1 미만이거나 조회 상한(page × size > 10000) 초과(INVALID_PAGE) / size가 음수(INVALID_SIZE)")
    @GetMapping("/authors")
    public ApiResponse<CommunityPage<MemberProfileInfo>> getAuthors(
            @Parameter(description = "활동 분야 필터") @RequestParam(required = false) ActivityField activityField,
            @Parameter(description = "정렬 기준 (RECENTLY_UPDATED·VIEW_COUNT·EXPERIENCE, 기본 RECENTLY_UPDATED)") @RequestParam(required = false) ProfileSort sort,
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        int resolvedSize = resolveSize(size);
        int resolvedPage = resolvePage(page, resolvedSize);
        if (resolvedSize == 0) {
            return ApiResponse.success(emptyPage(resolvedPage));
        }
        SearchProfilesCommand command = new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE),
                activityField, sort, viewerLanguages(getOptionalMemberId()), resolvedPage, resolvedSize);
        return ApiResponse.success(CommunityPage.of(
                memberService.searchProfiles(command), resolvedPage, resolvedSize));
    }

    @Operation(summary = "구인글 탭", description = "구인글 카드 목록을 PUBLISHED 상태만 페이지 단위로 조회합니다. 인증 불필요. "
            + "totalCount는 PUBLISHED 구인글 전체 수입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "page가 1 미만이거나 조회 상한(page × size > 10000) 초과(INVALID_PAGE) / size가 음수(INVALID_SIZE)")
    @GetMapping("/job-postings")
    public ApiResponse<CommunityPage<CommunityJobPostingCardInfo>> getJobPostings(
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        int resolvedSize = resolveSize(size);
        int resolvedPage = resolvePage(page, resolvedSize);
        if (resolvedSize == 0) {
            return ApiResponse.success(emptyPage(resolvedPage));
        }
        return ApiResponse.success(CommunityPage.of(
                recruitService.getJobPostingFeed(resolvedPage, resolvedSize), resolvedPage, resolvedSize));
    }

    @Operation(summary = "팀원모집글 탭", description = "팀원모집글 카드 목록을 PUBLISHED 상태만 페이지 단위로 조회합니다. 인증 불필요. "
            + "totalCount는 PUBLISHED 팀원모집글 전체 수입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "page가 1 미만이거나 조회 상한(page × size > 10000) 초과(INVALID_PAGE) / size가 음수(INVALID_SIZE)")
    @GetMapping("/team-recruits")
    public ApiResponse<CommunityPage<CommunityTeamRecruitCardInfo>> getTeamRecruits(
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        int resolvedSize = resolveSize(size);
        int resolvedPage = resolvePage(page, resolvedSize);
        if (resolvedSize == 0) {
            return ApiResponse.success(emptyPage(resolvedPage));
        }
        return ApiResponse.success(CommunityPage.of(
                recruitService.getTeamRecruitFeed(resolvedPage, resolvedSize), resolvedPage, resolvedSize));
    }

    /**
     * 페이지 번호는 1부터 센다(피그마 표기와 같다). 깊은 페이지는 OFFSET이 건너뛸 행을 실제로 읽어
     * 비용이 선형으로 늘어나므로 상한을 넘으면 거부한다 — 상한 값은 운영 데이터가 늘면 재검토한다.
     */
    private int resolvePage(Integer page, int size) {
        if (page == null) {
            return 1;
        }
        // size=0이면 곱이 0이라 어떤 페이지든 통과해버리므로 1로 보고 판정한다(page 자체를 상한에 묶는다).
        long reach = (long) page * Math.max(size, 1);
        if (page < 1 || reach > MAX_OFFSET) {
            throw new CommunityException(CommunityErrorCode.INVALID_PAGE);
        }
        return page;
    }

    /**
     * size=0은 "빈 목록"을 돌려주는 기존 동작이다(2026-08-28 prod 이슈 이후 유지). 조회 계층의
     * {@code PageRequest}는 0을 거부하므로 여기서 끊는다 — 조회도 개수 세기도 하지 않는다.
     */
    private <T> CommunityPage<T> emptyPage(int page) {
        return CommunityPage.of(OffsetPage.<T>empty(), page, 0);
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        // 음수는 그대로 흘려보내면 조회 계층에서 500이 된다(2026-08-28 prod에서 확인).
        // 0은 빈 목록을 돌려주는 기존 동작이라 그대로 둔다 — 음수만 막는다.
        if (size < 0) {
            throw new CommunityException(CommunityErrorCode.INVALID_SIZE);
        }
        return Math.min(size, MAX_SIZE);
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
}
