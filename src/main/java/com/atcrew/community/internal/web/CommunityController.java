package com.atcrew.community.internal.web;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.community.internal.exception.CommunityErrorCode;
import com.atcrew.community.internal.exception.CommunityException;
import com.atcrew.member.ActivityField;
import com.atcrew.member.EmploymentStatus;
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
            "공개(PUBLIC)이면서 이미지 처리가 끝난(READY) 작품만 등록일(createdAt) 내림차순으로 조회합니다. "
            + "다음 페이지는 응답의 nextCursor를 cursor로 그대로 전달해 요청하며, hasNext=false면 마지막 페이지입니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/artworks")
    public ApiResponse<CursorPage<ArtworkSummaryInfo>> getArtworks(
            @Parameter(description = "작품 분야 필터 — 단일 선택. 미지정 시 전체 분야",
                    example = "ILLUSTRATION") @RequestParam(required = false) ArtworkField artworkField,
            @Parameter(description = "연령 등급 필터 — 단일 선택. 미지정 시 전체 등급(전체연령가+성인물)이 함께 조회됩니다",
                    example = "ALL") @RequestParam(required = false) AgeRating ageRating,
            @Parameter(description = "커서 — 직전 응답의 nextCursor 값(마지막 작품 createdAt epochMilli 문자열). "
                    + "첫 페이지는 생략. 숫자가 아니면 400 INVALID_CURSOR",
                    example = "1786496839000") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 50 초과 시 50으로 잘립니다. "
                    + "0은 빈 목록, 1 미만이면 400 INVALID_SIZE",
                    example = "20") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(artworkService.getCommunityArtworks(
                artworkField, ageRating, cursor, resolveSize(size)));
    }

    @Operation(summary = "작가 프로필 탭 — 작가 찾아보기", description =
            "구인 가능 상태(AVAILABLE 신규 작업 가능 · NEGOTIABLE 협의 가능)인 활성 회원의 프로필 목록을 조회합니다. "
            + "구인 가능 상태 조건은 서버가 고정하며 클라이언트가 바꿀 수 없습니다. "
            + "다음 페이지는 응답의 nextCursor를 cursor로 그대로 전달해 요청합니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/authors")
    public ApiResponse<CursorPage<MemberProfileInfo>> getAuthors(
            @Parameter(description = "활동 분야 필터 — 단일 선택. 해당 분야를 가진 프로필만 조회하며, 미지정 시 전체",
                    example = "ILLUSTRATION") @RequestParam(required = false) ActivityField activityField,
            @Parameter(description = "정렬 기준 — RECENTLY_UPDATED(최신 업데이트순, 기본값) 또는 EXPERIENCE(경력순). "
                    + "정렬 기준에 따라 커서 형식이 달라지므로 페이지를 넘기는 도중에는 값을 바꾸지 마세요",
                    example = "RECENTLY_UPDATED") @RequestParam(required = false) ProfileSort sort,
            @Parameter(description = "커서 — 직전 응답의 nextCursor 값을 그대로 전달합니다. "
                    + "RECENTLY_UPDATED는 `updatedAt epochMilli`, EXPERIENCE는 `경력랭크_updatedAt epochMilli` 형식이며, "
                    + "형식이 어긋나면 400 INVALID_CURSOR",
                    example = "1786496839000") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 50 초과 시 50으로 잘립니다. "
                    + "0은 빈 목록, 1 미만이면 400 INVALID_SIZE",
                    example = "20") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE),
                activityField, sort, cursor, resolveSize(size))));
    }

    @Operation(summary = "구인글 탭", description =
            "게시 중(PUBLISHED)인 구인글 카드 목록을 조회합니다. 끌어올리기(boost)가 적용 중인 글이 상단에 고정되고, "
            + "그 다음은 최신 등록순입니다. 마감(CLOSED)된 글은 목록에서 제외되므로 카드의 closed는 항상 false입니다. "
            + "필터·정렬 파라미터는 없습니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings")
    public ApiResponse<CursorPage<CommunityJobPostingCardInfo>> getJobPostings(
            @Parameter(description = "커서 — 직전 응답의 nextCursor 값(`정렬키 epochMilli_글ID` 형식)을 그대로 전달합니다. "
                    + "첫 페이지는 생략하며, 형식이 어긋나면 400 INVALID_CURSOR",
                    example = "0_019ff382-ccdc-71bb-bccb-6a3c35d33978") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 50 초과 시 50으로 잘립니다. "
                    + "0은 빈 목록, 1 미만이면 400 INVALID_SIZE",
                    example = "20") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getJobPostingFeed(cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 탭", description =
            "게시 중(PUBLISHED)인 팀원모집글 카드 목록을 조회합니다. 끌어올리기(boost)가 적용 중인 글이 상단에 고정되고, "
            + "그 다음은 최신 등록순입니다. 마감(CLOSED)된 글은 목록에서 제외되므로 카드의 closed는 항상 false입니다. "
            + "필터·정렬 파라미터는 없습니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-recruits")
    public ApiResponse<CursorPage<CommunityTeamRecruitCardInfo>> getTeamRecruits(
            @Parameter(description = "커서 — 직전 응답의 nextCursor 값(`정렬키 epochMilli_글ID` 형식)을 그대로 전달합니다. "
                    + "첫 페이지는 생략하며, 형식이 어긋나면 400 INVALID_CURSOR",
                    example = "0_019ff382-ccdc-71bb-bccb-6a3c35d33978") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 — 기본 20, 50 초과 시 50으로 잘립니다. "
                    + "0은 빈 목록, 1 미만이면 400 INVALID_SIZE",
                    example = "20") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getTeamRecruitFeed(cursor, resolveSize(size)));
    }

    private int resolveSize(Integer size) {
        if (size == null) return DEFAULT_SIZE;
        if (size < 1) throw new CommunityException(CommunityErrorCode.INVALID_SIZE);
        return Math.min(size, MAX_SIZE);
    }
}
