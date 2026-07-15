package com.atcrew.community;

import java.time.Instant;

public record BannerInfo(
        String id,
        String memberId,
        String imageUrl,
        String linkUrl,
        int sortOrder,
        BannerStatus status,
        Instant createdAt
) {
}
