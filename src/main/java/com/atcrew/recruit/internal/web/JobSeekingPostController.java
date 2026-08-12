package com.atcrew.recruit.internal.web;

import com.atcrew.common.response.ApiResponse;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.security.MemberPrincipal;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.recruit.CreateJobSeekingPostCommand;
import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.UpdateJobSeekingPostCommand;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import com.atcrew.recruit.internal.web.dto.CreateJobSeekingPostRequest;
import com.atcrew.recruit.internal.web.dto.UpdateJobSeekingPostRequest;
import com.atcrew.recruit.internal.web.dto.UpdateJobSeekingPostStatusRequest;
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
 * 구직글 CRUD API (docs/design/recruit-module-design.md §4.2).
 * 관리자 승인 절차가 없으므로 JobPosting과 달리 submit/approve/reject 엔드포인트가 없다.
 */
// 2026-08 출시 마일스톤 범위 밖(구인구직/기업 프로필) — Swagger 노출만 숨김, API 자체는 유지
@Hidden
@Tag(name = "구직글", description = "구직글 작성·수정·조회·상태전이 API")
@Validated
@RestController
@RequestMapping("/api/recruit")
class JobSeekingPostController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final RecruitService recruitService;
    private final SecurityUtils securityUtils;

    JobSeekingPostController(RecruitService recruitService, SecurityUtils securityUtils) {
        this.recruitService = recruitService;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "구직글 작성", description = "DRAFT로 저장하거나, publish=true면 저장 직후 PUBLISHED로 게시합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공")
    @PostMapping("/job-seeking-posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobSeekingPostInfo> createJobSeekingPost(
            @RequestBody @Valid CreateJobSeekingPostRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.createJobSeekingPost(memberId, toCommand(request)));
    }

    @Operation(summary = "구직글 수정", description = "작성자 본인만 가능합니다. 휴지통에 있는 구직글은 수정할 수 없습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @PutMapping("/job-seeking-posts/{jobSeekingPostId}")
    public ApiResponse<JobSeekingPostInfo> updateJobSeekingPost(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId,
            @RequestBody @Valid UpdateJobSeekingPostRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.updateJobSeekingPost(memberId, jobSeekingPostId, toCommand(request)));
    }

    @Operation(summary = "구직글 게시", description = "DRAFT/CLOSED → PUBLISHED. 승인 절차 없이 즉시 공개됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "게시 성공")
    @PatchMapping("/job-seeking-posts/{jobSeekingPostId}/publish")
    public ApiResponse<JobSeekingPostInfo> publishJobSeekingPost(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.publishJobSeekingPost(memberId, jobSeekingPostId));
    }

    @Operation(summary = "구직글 상태 변경", description = "현재는 CLOSED(마감) 전이만 지원합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @PatchMapping("/job-seeking-posts/{jobSeekingPostId}/status")
    public ApiResponse<JobSeekingPostInfo> updateJobSeekingPostStatus(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId,
            @RequestBody @Valid UpdateJobSeekingPostStatusRequest request) {
        String memberId = securityUtils.getCurrentMemberId();
        if (request.status() != JobSeekingPostStatus.CLOSED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION,
                    "지원하지 않는 상태 변경 요청: " + request.status());
        }
        return ApiResponse.success(recruitService.closeJobSeekingPost(memberId, jobSeekingPostId));
    }

    @Operation(summary = "구직글 삭제 (휴지통 이동)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/job-seeking-posts/{jobSeekingPostId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJobSeekingPost(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId) {
        String memberId = securityUtils.getCurrentMemberId();
        recruitService.deleteJobSeekingPost(memberId, jobSeekingPostId);
    }

    @Operation(summary = "구직글 휴지통 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-seeking-posts/trash")
    public ApiResponse<CursorPage<JobSeekingPostInfo>> getTrashedJobSeekingPosts(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getTrashedJobSeekingPosts(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "구직글 복구", description = "휴지통에 있는 구직글을 DRAFT로 복구합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복구 성공")
    @PatchMapping("/job-seeking-posts/{jobSeekingPostId}/restore")
    public ApiResponse<JobSeekingPostInfo> restoreJobSeekingPost(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.restoreJobSeekingPost(memberId, jobSeekingPostId));
    }

    @Operation(summary = "내 구직글 목록", description = "휴지통(DELETED)을 제외한 모든 상태를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-seeking-posts/me")
    public ApiResponse<CursorPage<JobSeekingPostInfo>> getMyJobSeekingPosts(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        String memberId = securityUtils.getCurrentMemberId();
        return ApiResponse.success(recruitService.getMyJobSeekingPosts(memberId, cursor, resolveSize(size)));
    }

    @Operation(summary = "구직글 상세 조회", description = "PUBLISHED만 공개 노출됩니다. 작성자 본인은 모든 상태를 조회할 수 있습니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-seeking-posts/{jobSeekingPostId}")
    public ApiResponse<JobSeekingPostInfo> getJobSeekingPost(
            @Parameter(description = "구직글 ID") @PathVariable @Pattern(regexp = UUID_PATTERN, message = "구직글 ID 형식이 올바르지 않습니다") String jobSeekingPostId) {
        String viewerId = getOptionalMemberId();
        return ApiResponse.success(recruitService.getJobSeekingPost(jobSeekingPostId, viewerId));
    }

    @Operation(summary = "구직글 목록(커서)", description = "PUBLISHED 상태만 조회됩니다. 인증 불필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/job-seeking-posts")
    public ApiResponse<CursorPage<JobSeekingPostInfo>> getJobSeekingPosts(
            @Parameter(description = "커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)") @RequestParam(required = false) Integer size) {
        return ApiResponse.success(recruitService.getJobSeekingPosts(cursor, resolveSize(size)));
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

    private CreateJobSeekingPostCommand toCommand(CreateJobSeekingPostRequest r) {
        return new CreateJobSeekingPostCommand(
                r.title(), r.roles(), r.genres(), r.drawingStyle(), r.preferredFeedbackStyle(), r.workStyle(),
                r.desiredRate(), r.portfolioDescription(), r.referenceImages(), r.publish());
    }

    private UpdateJobSeekingPostCommand toCommand(UpdateJobSeekingPostRequest r) {
        return new UpdateJobSeekingPostCommand(
                r.title(), r.roles(), r.genres(), r.drawingStyle(), r.preferredFeedbackStyle(), r.workStyle(),
                r.desiredRate(), r.portfolioDescription(), r.referenceImages());
    }
}
