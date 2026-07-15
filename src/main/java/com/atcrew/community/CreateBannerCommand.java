package com.atcrew.community;

public record CreateBannerCommand(
        String memberId,
        String imageUrl,
        String linkUrl,
        Integer sortOrder // null이면 마지막 순번 자동 부여
) {
}
