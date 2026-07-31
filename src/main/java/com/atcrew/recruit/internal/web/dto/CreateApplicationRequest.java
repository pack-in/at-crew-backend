package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.SerialExperience;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 구인글/팀원모집글 지원 요청 — 요구 필드가 동일하므로 하나를 공유한다.
public record CreateApplicationRequest(
        @NotNull SerialExperience serialExperience,
        boolean assistantExperience,
        @Size(max = 500) String resumeUrl
) {
}
