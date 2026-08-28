package com.atcrew.community.internal.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBannerRequest(
        @NotBlank @Size(max = 36) String memberId,
        @NotBlank @Size(max = 500) String imageUrl,
        @NotBlank @Size(max = 500) String linkUrl,
        @Min(0) Integer sortOrder // 선택 — 미지정 시 마지막 순번 자동 부여
) {
}
