package com.atcrew.community.internal.web;

import com.atcrew.community.internal.exception.CommunityErrorCode;
import com.atcrew.community.internal.exception.CommunityException;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSort;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
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
            + "정렬 기준을 바꾸면 커서의 의미도 달라지므로 cursor 없이 첫 페이지부터 다시 요청해야 합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "커서가 \"정렬값_작품ID\" 형식이 아님(INVALID_CURSOR)")
    @GetMapping("/artworks")
    public ApiResponse<CursorPage<ArtworkSummaryInfo>> getArtworks(
            @Parameter(description = "작품 분야 필터") @RequestParam(required = false) ArtworkField artworkField,
            @Parameter(description = "연령 등급 필터 (기본 ALL)") @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "정렬 기준 (LATEST·OLDEST·VIEW_COUNT·BOOKMARK_COUNT, 기본 LATEST)")
            @RequestParam(required = false, defaultValue = "LATEST") ArtworkSort sort,
            @Parameter(description = "커서 (직전 응답의 nextCursor를 그대로 전달)") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(artworkService.getCommunityArtworks(
                artworkField, ageRating, viewerLanguages(), sort, cursor, resolveSize(size)));
    }

    @Operation(summary = "작가 프로필 탭 — 작가 찾아보기", description =
            "구인 가능 상태(신규 작업 가능·협의 가능)인 창작자 프로필 목록을 조회합니다. 인증 불필요. "
            + "노출 대상 항목(사용자 이름·활동 분야·활동 경력·희망 담당 업무·희망 장르·희망 채용 형태·연락처)이 "
            + "비어 있는 프로필은 제외됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/authors")
    public ApiResponse<CursorPage<MemberProfileInfo>> getAuthors(
            @Parameter(description = "활동 분야 필터") @RequestParam(required = false) ActivityField activityField,
            @Parameter(description = "정렬 기준 (RECENTLY_UPDATED·VIEW_COUNT·EXPERIENCE, 기본 RECENTLY_UPDATED)") @RequestParam(required = false) ProfileSort sort,
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE),
                activityField, sort, viewerLanguages(), cursor, resolveSize(size))));
    }

    @Operation(summary = "구인글 탭", description = "구인글 카드 목록을 PUBLISHED 상태만 커서 페이지네이션으로 조회합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings")
    public ApiResponse<CursorPage<CommunityJobPostingCardInfo>> getJobPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getJobPostingFeed(cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 탭", description = "팀원모집글 카드 목록을 PUBLISHED 상태만 커서 페이지네이션으로 조회합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-recruits")
    public ApiResponse<CursorPage<CommunityTeamRecruitCardInfo>> getTeamRecruits(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50). 0은 빈 목록, 음수는 400 INVALID_SIZE") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getTeamRecruitFeed(cursor, resolveSize(size)));
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
    private List<Language> viewerLanguages() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal principal) {
            return memberService.findPostLanguages(principal.memberId());
        }
        return List.of();
    }
}
