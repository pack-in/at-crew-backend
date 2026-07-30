package com.atcrew.community;

public record UpdateBannerCommand(
        String imageUrl, // null이면 기존 값 유지
        String linkUrl,
        Integer sortOrder
) {
}
