package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.UpdateJobPostingCommand;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.web.dto.CreateJobPostingRequest;
import com.atcrew.recruit.internal.web.dto.UpdateJobPostingRequest;
import com.atcrew.recruit.internal.web.dto.UpdateJobPostingStatusRequest;
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
 * 구인글 CRUD + PENDING 승인 API (docs/design/recruit-module-design.md §4.1).
 * 관리자 엔드포인트는 별도 Role 체계가 도입되기 전까지 인증만 요구하는 최소 구현이다(§7).
 */
@Tag(name = "구인글", description = "구인글 작성·수정·조회·상태전이·관리자 승인 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class JobPostingController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final RecruitService recruitService;
    private final SecurityUtils securityUtils;

    JobPostingController(RecruitService recruitService, SecurityUtils securityUtils) {
        this.recruitService = recruitService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "구인글 작성", description = "DRAFT로 저장하거나, submit=true면 저장 직후 PENDING으로 제출합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공")
    @PostMapping("/job-postings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobPostingInfo> createJobPosting(@RequestBody @Valid CreateJobPostingRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.createJobPosting(memberId, toCommand(request)));
    }

    @Operation(summary = "구인글 수정", description = "작성자 본인만 가능합니다. 휴지통에 있는 구인글은 수정할 수 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PutMapping("/job-postings/{jobPostingId}")
    public ApiResponse<JobPostingInfo> updateJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @RequestBody @Valid UpdateJobPostingRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.updateJobPosting(memberId, jobPostingId, toCommand(request)));
    }

    @Operation(summary = "구인글 제출", description = "DRAFT/PENDING → PENDING. 관리자 승인 대기 상태로 전환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공")
    @PatchMapping("/job-postings/{jobPostingId}/submit")
    public ApiResponse<JobPostingInfo> submitJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.submitJobPosting(memberId, jobPostingId));
    }

    @Operation(summary = "구인글 상태 변경", description = "현재는 CLOSED(마감) 전이만 지원합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/job-postings/{jobPostingId}/status")
    public ApiResponse<JobPostingInfo> updateJobPostingStatus(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId,
            @RequestBody @Valid UpdateJobPostingStatusRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        if (request.status() != JobPostingStatus.CLOSED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION,
                    "지원하지 않는 상태 변경 요청: " + request.status());
        }
        return ApiResponse.success(recruitService.closeJobPosting(memberId, jobPostingId));
    }

    @Operation(summary = "구인글 삭제 (휴지통 이동)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/job-postings/{jobPostingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        recruitService.deleteJobPosting(memberId, jobPostingId);
    }

    @Operation(summary = "휴지통 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings/trash")
    public ApiResponse<CursorPage<JobPostingInfo>> getTrashedJobPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getTrashedJobPostings(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "구인글 복구", description = "휴지통에 있는 구인글을 DRAFT로 복구합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복구 성공")
    @PatchMapping("/job-postings/{jobPostingId}/restore")
    public ApiResponse<JobPostingInfo> restoreJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.restoreJobPosting(memberId, jobPostingId));
    }

    @Operation(summary = "내 구인글 목록", description = "휴지통(DELETED)을 제외한 모든 상태를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings/me")
    public ApiResponse<CursorPage<JobPostingInfo>> getMyJobPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getMyJobPostings(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "구인글 상세 조회", description = "PUBLISHED만 공개 노출됩니다. 작성자 본인은 모든 상태를 조회할 수 있습니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings/{jobPostingId}")
    public ApiResponse<JobPostingInfo> getJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(recruitService.getJobPosting(jobPostingId, viewerId));
    }

    @Operation(summary = "구인글 목록(커서)", description = "PUBLISHED 상태만 조회됩니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-postings")
    public ApiResponse<CursorPage<JobPostingInfo>> getJobPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getJobPostings(cursor, resolveSize(size)));
    }

    @Operation(summary = "구인글 끌어올리기", description =
            "48시간 동안 목록 상단에 고정합니다. 작성자 본인만 가능하며, 이미 적용 중이면 409로 거부됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "적용 성공")
    @PatchMapping("/job-postings/{jobPostingId}/boost")
    public ApiResponse<JobPostingInfo> boostJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.boostJobPosting(memberId, jobPostingId));
    }

    // === 관리자 — TODO: 관리자 Role 체계 도입 후 @PreAuthorize("hasRole('ADMIN')") 추가(설계 §7) ===

    @Operation(summary = "[관리자] PENDING 구인글 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/admin/job-postings")
    public ApiResponse<CursorPage<JobPostingInfo>> getPendingJobPostings(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        securityUtils.getCurrentMemberId(); // 인증만 요구 — 관리자 Role 체계는 후속 작업
        return ApiResponse.success(recruitService.getPendingJobPostings(cursor, resolveSize(size)));
    }

    @Operation(summary = "[관리자] 구인글 승인", description = "PENDING → PUBLISHED")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 성공")
    @PatchMapping("/admin/job-postings/{jobPostingId}/approve")
    public ApiResponse<JobPostingInfo> approveJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        securityUtils.getCurrentMemberId(); // 인증만 요구 — 관리자 Role 체계는 후속 작업
        return ApiResponse.success(recruitService.approveJobPosting(jobPostingId));
    }

    @Operation(summary = "[관리자] 구인글 반려", description = "PENDING → CLOSED")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반려 성공")
    @PatchMapping("/admin/job-postings/{jobPostingId}/reject")
    public ApiResponse<JobPostingInfo> rejectJobPosting(
            @Parameter(description = "구인글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구인글 ID 형식이 올바르지 않습니다") String jobPostingId) {
        securityUtils.getCurrentMemberId(); // 인증만 요구 — 관리자 Role 체계는 후속 작업
        return ApiResponse.success(recruitService.rejectJobPosting(jobPostingId));
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

    private CreateJobPostingCommand toCommand(CreateJobPostingRequest r) {
        return new CreateJobPostingCommand(
                r.title(), r.companyName(), r.ceoName(), r.industry(), r.address(), r.contact(), r.websiteUrl(),
                r.companyDescription(), r.isBusinessRegistered(), r.isResumeRequired(), r.isCoverLetterRequired(),
                r.roles(), r.genres(), r.workScope(), r.deadline(), r.recruitCount(), r.hiringProcess(),
                r.education(), r.experience(), r.age(), r.gender(), r.employmentType(), r.workLocationType(),
                r.workScheduleType(), r.coreTimeStart(), r.coreTimeEnd(), r.hasOvertimePay(), r.hasSocialInsurance(),
                r.hasContract(), r.paymentType(), r.paymentUnit(), r.minAmount(), r.maxAmount(), r.isNegotiable(),
                r.mgAmount(), r.rsRatio(), r.hasBuyout(), r.benefitDescription(), r.benefitKeywords(),
                r.thumbnailImage(), r.referenceImages(), r.submit());
    }

    private UpdateJobPostingCommand toCommand(UpdateJobPostingRequest r) {
        return new UpdateJobPostingCommand(
                r.title(), r.companyName(), r.ceoName(), r.industry(), r.address(), r.contact(), r.websiteUrl(),
                r.companyDescription(), r.isBusinessRegistered(), r.isResumeRequired(), r.isCoverLetterRequired(),
                r.roles(), r.genres(), r.workScope(), r.deadline(), r.recruitCount(), r.hiringProcess(),
                r.education(), r.experience(), r.age(), r.gender(), r.employmentType(), r.workLocationType(),
                r.workScheduleType(), r.coreTimeStart(), r.coreTimeEnd(), r.hasOvertimePay(), r.hasSocialInsurance(),
                r.hasContract(), r.paymentType(), r.paymentUnit(), r.minAmount(), r.maxAmount(), r.isNegotiable(),
                r.mgAmount(), r.rsRatio(), r.hasBuyout(), r.benefitDescription(), r.benefitKeywords(),
                r.thumbnailImage(), r.referenceImages());
    }
}
