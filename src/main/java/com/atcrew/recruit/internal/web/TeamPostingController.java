package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.UpdateTeamPostingCommand;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.web.dto.CreateTeamPostingRequest;
import com.atcrew.recruit.internal.web.dto.UpdateTeamPostingRequest;
import com.atcrew.recruit.internal.web.dto.UpdateTeamPostingStatusRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀원모집글 CRUD API (docs/design/recruit-module-design.md §4.2).
 * JobPosting과 달리 승인 절차가 없어 제출/관리자 승인·반려 엔드포인트가 없다(생성 즉시 PUBLISHED).
 */
// 2026-08 출시 마일스톤 범위 밖(구인구직/기업 프로필) — Swagger 노출만 숨김, API 자체는 유지
@Hidden
@Tag(name = "팀원모집글", description = "팀원모집글 작성·수정·조회·상태전이 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class TeamPostingController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final RecruitService recruitService;
    private final SecurityUtils securityUtils;

    TeamPostingController(RecruitService recruitService, SecurityUtils securityUtils) {
        this.recruitService = recruitService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "팀원모집글 작성", description = "승인 절차 없이 저장 즉시 PUBLISHED로 게시됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공")
    @PostMapping("/team-postings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamPostingInfo> createTeamPosting(@RequestBody @Valid CreateTeamPostingRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.createTeamPosting(memberId, toCommand(request)));
    }

    @Operation(summary = "팀원모집글 수정", description = "작성자 본인만 가능합니다. 휴지통에 있는 팀원모집글은 수정할 수 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PutMapping("/team-postings/{teamPostingId}")
    public ApiResponse<TeamPostingInfo> updateTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @RequestBody @Valid UpdateTeamPostingRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.updateTeamPosting(memberId, teamPostingId, toCommand(request)));
    }

    @Operation(summary = "팀원모집글 상태 변경", description = "현재는 CLOSED(마감) 전이만 지원합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/team-postings/{teamPostingId}/status")
    public ApiResponse<TeamPostingInfo> updateTeamPostingStatus(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @RequestBody @Valid UpdateTeamPostingStatusRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        if (request.status() != TeamPostingStatus.CLOSED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION,
                    "지원하지 않는 상태 변경 요청: " + request.status());
        }
        return ApiResponse.success(recruitService.closeTeamPosting(memberId, teamPostingId));
    }

    @Operation(summary = "팀원모집글 삭제 (휴지통 이동)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/team-postings/{teamPostingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        recruitService.deleteTeamPosting(memberId, teamPostingId);
    }

    @Operation(summary = "휴지통 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-postings/trash")
    public ApiResponse<CursorPage<TeamPostingInfo>> getTrashedTeamPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getTrashedTeamPostings(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 복구", description = "휴지통에 있는 팀원모집글을 PUBLISHED로 즉시 복구합니다(승인 절차 없음).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복구 성공")
    @PatchMapping("/team-postings/{teamPostingId}/restore")
    public ApiResponse<TeamPostingInfo> restoreTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.restoreTeamPosting(memberId, teamPostingId));
    }

    @Operation(summary = "내 팀원모집글 목록", description = "휴지통(DELETED)을 제외한 모든 상태를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-postings/me")
    public ApiResponse<CursorPage<TeamPostingInfo>> getMyTeamPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getMyTeamPostings(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 상세 조회", description = "PUBLISHED만 공개 노출됩니다. 작성자 본인은 모든 상태를 조회할 수 있습니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-postings/{teamPostingId}")
    public ApiResponse<TeamPostingInfo> getTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(recruitService.getTeamPosting(teamPostingId, viewerId));
    }

    @Operation(summary = "팀원모집글 목록(커서)", description = "PUBLISHED 상태만 조회됩니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-postings")
    public ApiResponse<CursorPage<TeamPostingInfo>> getTeamPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getTeamPostings(cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 끌어올리기", description =
            "48시간 동안 목록 상단에 고정합니다. 작성자 본인만 가능하며, 이미 적용 중이면 409로 거부됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "적용 성공")
    @PatchMapping("/team-postings/{teamPostingId}/boost")
    public ApiResponse<TeamPostingInfo> boostTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.boostTeamPosting(memberId, teamPostingId));
    }

    private int resolveSize(Integer size) {
        return size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
    }

    private String getOptionalMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof MemberPrincipal p) {
            return p.memberId();
        }
        return null;
    }

    private CreateTeamPostingCommand toCommand(CreateTeamPostingRequest r) {
        return new CreateTeamPostingCommand(
                r.title(), r.isBusinessRegistered(), r.isResumeRequired(), r.isCoverLetterRequired(),
                r.authorName(), r.contact(), r.authorDescription(), r.recruitPurposes(), r.workLocationType(),
                r.activityRegion(), r.roles(), r.genres(), r.hasParticipationFee(), r.hasProfitSharing(),
                r.extraCost(), r.deadline(), r.recruitCount(), r.selectionProcess(), r.activityDuration(),
                r.weeklyActivityTime(), r.projectDescription(), r.thumbnailImage(), r.referenceImages());
    }

    private UpdateTeamPostingCommand toCommand(UpdateTeamPostingRequest r) {
        return new UpdateTeamPostingCommand(
                r.title(), r.isBusinessRegistered(), r.isResumeRequired(), r.isCoverLetterRequired(),
                r.authorName(), r.contact(), r.authorDescription(), r.recruitPurposes(), r.workLocationType(),
                r.activityRegion(), r.roles(), r.genres(), r.hasParticipationFee(), r.hasProfitSharing(),
                r.extraCost(), r.deadline(), r.recruitCount(), r.selectionProcess(), r.activityDuration(),
                r.weeklyActivityTime(), r.projectDescription(), r.thumbnailImage(), r.referenceImages());
    }
}
