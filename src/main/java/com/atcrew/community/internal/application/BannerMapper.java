package com.atcrew.community.internal.application;

import com.atcrew.community.BannerInfo;
import com.atcrew.community.internal.domain.Banner;

class BannerMapper {

    private BannerMapper() {
    }

    static BannerInfo toInfo(Banner banner) {
        return new BannerInfo(
                banner.getId(),
                banner.getMemberId(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.getSortOrder(),
                banner.getStatus(),
                banner.getCreatedAt()
        );
    }
}
