package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.JobPostingStatus;
import jakarta.validation.constraints.NotNull;

// 현재는 CLOSED(작성자 마감) 전이만 지원한다. 그 외 값은 RecruitErrorCode.INVALID_STATUS_TRANSITION으로 거부된다.
public record UpdateJobPostingStatusRequest(
        @NotNull JobPostingStatus status
) {
}
