package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.recruit.ApplicationInfo;
import com.atcrew.recruit.CreateApplicationCommand;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.internal.web.dto.CreateApplicationRequest;
import com.atcrew.recruit.internal.web.dto.UpdateApplicationReviewStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
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

/**
 * 지원 및 지원자 관리 API (docs/design/recruit-module-design.md §4.1, §2.5, §2.6).
 * 지원자 목록·상태 변경·삭제는 구인글/팀원모집글 모두 작성자 소유권을 검증한다.
 */
@Tag(name = "지원/지원자 관리", description = "구인글·팀원모집글 지원 및 지원자 채용 단계 관리 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class ApplicationController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final RecruitService recruitService;
    private final SecurityUtils securityUtils;

    ApplicationController(RecruitService recruitService, SecurityUtils securityUtils) {
        this.recruitService = recruitService;
        this.securityUtils = securityUtils;
    }

    // === 구인글 지원 ===

    @Operation(summary = "구인글 지원", description = "PUBLISHED 상태의 구인글에만 지원할 수 있습니다. 중복 지원은 409로 거부됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "지원 성공")
    @PostMapping("/job-postings/{jobPostingId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicationInfo> applyToJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @RequestBody @Valid CreateApplicationRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.applyToJobPosting(memberId, jobPostingId, toCommand(request)));
    }

    @Operation(summary = "구인글 지원자 목록", description = "작성자 본인만 조회할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings/{jobPostingId}/applications")
    public ApiResponse<CursorPage<ApplicationInfo>> getJobPostingApplications(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(
                recruitService.getJobPostingApplications(memberId, jobPostingId, cursor, resolveSize(size)));
    }

    @Operation(summary = "구인글 지원자 채용 단계 변경", description = "작성자 본인만 변경할 수 있습니다. 최종 합격 처리도 ACCEPTED로 변경합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/job-postings/{jobPostingId}/applications/{applicationId}/review-status")
    public ApiResponse<ApplicationInfo> updateJobApplicationReviewStatus(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @Parameter(description = "지원 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "지원 ID 형식이 올바르지 않습니다") String applicationId,
            @RequestBody @Valid UpdateApplicationReviewStatusRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.updateJobApplicationReviewStatus(
                memberId, jobPostingId, applicationId, request.reviewStatus()));
    }

    @Operation(summary = "구인글 지원 내역 삭제", description = "작성자 본인만 삭제할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/job-postings/{jobPostingId}/applications/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJobApplication(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @Parameter(description = "지원 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "지원 ID 형식이 올바르지 않습니다") String applicationId) {
        String memberId = securityUtils.getCurrentMemberId();
        recruitService.deleteJobApplication(memberId, jobPostingId, applicationId);
    }

    // === 팀원모집글 지원 — 구인글과 동일한 소유권 검증을 대칭 적용(§2.6) ===

    @Operation(summary = "팀원모집글 지원", description = "PUBLISHED 상태의 팀원모집글에만 지원할 수 있습니다. 중복 지원은 409로 거부됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "지원 성공")
    @PostMapping("/team-postings/{teamPostingId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicationInfo> applyToTeamPosting(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @RequestBody @Valid CreateApplicationRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.applyToTeamPosting(memberId, teamPostingId, toCommand(request)));
    }

    @Operation(summary = "팀원모집글 지원자 목록", description = "작성자 본인만 조회할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/team-postings/{teamPostingId}/applications")
    public ApiResponse<CursorPage<ApplicationInfo>> getTeamPostingApplications(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(
                recruitService.getTeamPostingApplications(memberId, teamPostingId, cursor, resolveSize(size)));
    }

    @Operation(summary = "팀원모집글 지원자 채용 단계 변경", description = "작성자 본인만 변경할 수 있습니다. 최종 합격 처리도 ACCEPTED로 변경합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/team-postings/{teamPostingId}/applications/{applicationId}/review-status")
    public ApiResponse<ApplicationInfo> updateTeamApplicationReviewStatus(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @Parameter(description = "지원 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "지원 ID 형식이 올바르지 않습니다") String applicationId,
            @RequestBody @Valid UpdateApplicationReviewStatusRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.updateTeamApplicationReviewStatus(
                memberId, teamPostingId, applicationId, request.reviewStatus()));
    }

    @Operation(summary = "팀원모집글 지원 내역 삭제", description = "작성자 본인만 삭제할 수 있습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/team-postings/{teamPostingId}/applications/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeamApplication(
            @Parameter(description = "팀원모집글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "팀원모집글 ID 형식이 올바르지 않습니다") String teamPostingId,
            @Parameter(description = "지원 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "지원 ID 형식이 올바르지 않습니다") String applicationId) {
        String memberId = securityUtils.getCurrentMemberId();
        recruitService.deleteTeamApplication(memberId, teamPostingId, applicationId);
    }

    private int resolveSize(Integer size) {
        return size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
    }

    private CreateApplicationCommand toCommand(CreateApplicationRequest r) {
        return new CreateApplicationCommand(r.serialExperience(), r.assistantExperience(), r.resumeUrl());
    }
}
