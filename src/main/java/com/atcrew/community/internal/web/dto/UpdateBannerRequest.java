package com.atcrew.community.internal.web.dto;

public record UpdateBannerRequest(
        String imageUrl, // 선택 — null이면 기존 값 유지
        String linkUrl,
        Integer sortOrder
) {
}
