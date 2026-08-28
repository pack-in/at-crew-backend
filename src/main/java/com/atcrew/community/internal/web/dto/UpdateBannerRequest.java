package com.atcrew.community.internal.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateBannerRequest(
        @Size(max = 500) String imageUrl, // 선택 — null이면 기존 값 유지
        @Size(max = 500) String linkUrl,
        @Min(0) Integer sortOrder
) {
}
