package com.atcrew.community;

import java.util.List;

public interface BannerService {

    /** 활성(ACTIVE) 배너 목록을 sortOrder 오름차순으로 조회한다. */
    List<BannerInfo> getActiveBanners();

    BannerInfo createBanner(CreateBannerCommand command);

    BannerInfo updateBanner(String bannerId, UpdateBannerCommand command);

    /** soft delete — status를 DELETED로 변경한다. */
    void deleteBanner(String bannerId);
}
