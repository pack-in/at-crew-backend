package com.atcrew.community.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBannerRequest(
        @NotBlank String memberId,
        @NotBlank String imageUrl,
        @NotBlank String linkUrl,
        Integer sortOrder // 선택 — 미지정 시 마지막 순번 자동 부여
) {
}
