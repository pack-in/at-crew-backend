package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.ApplicationReviewStatus;
import jakarta.validation.constraints.NotNull;

// 지원자 채용 단계 변경 요청 (RECEIVED/REVIEWING/ACCEPTED/REJECTED)
public record UpdateApplicationReviewStatusRequest(
        @NotNull ApplicationReviewStatus reviewStatus
) {
}
