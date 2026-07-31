package com.atcrew.community.internal.application;

import com.atcrew.community.BannerInfo;
import com.atcrew.community.BannerService;
import com.atcrew.community.BannerStatus;
import com.atcrew.community.CreateBannerCommand;
import com.atcrew.community.UpdateBannerCommand;
import com.atcrew.community.internal.domain.Banner;
import com.atcrew.community.internal.exception.CommunityErrorCode;
import com.atcrew.community.internal.exception.CommunityException;
import com.atcrew.community.internal.persistence.BannerRepository;
import com.atcrew.member.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final MemberService memberService;

    BannerServiceImpl(BannerRepository bannerRepository, MemberService memberService) {
        this.bannerRepository = bannerRepository;
        this.memberService = memberService;
    }

    @Override
    public List<BannerInfo> getActiveBanners() {
        return bannerRepository.findByStatusOrderBySortOrderAsc(BannerStatus.ACTIVE).stream()
                .map(BannerMapper::toInfo)
                .toList();
    }

    @Override
    @Transactional
    public BannerInfo createBanner(CreateBannerCommand command) {
        memberService.findById(command.memberId()); // 존재하지 않으면 예외 전파

        int sortOrder;
        if (command.sortOrder() != null) {
            sortOrder = command.sortOrder();
            // 지정한 순번 이후 배너들을 한 칸씩 밀어 자리를 만든다.
            bannerRepository.shiftFromInclusive(sortOrder, Instant.now());
        } else {
            sortOrder = bannerRepository.findFirstByOrderBySortOrderDesc()
                    .map(b -> b.getSortOrder() + 1)
                    .orElse(0);
        }
        Banner banner = Banner.create(command.memberId(), command.imageUrl(), command.linkUrl(), sortOrder);
        return BannerMapper.toInfo(bannerRepository.save(banner));
    }

    @Override
    @Transactional
    public BannerInfo updateBanner(String bannerId, UpdateBannerCommand command) {
        Banner banner = findBannerById(bannerId);
        if (command.sortOrder() != null && command.sortOrder() != banner.getSortOrder()) {
            shiftForMove(banner.getId(), banner.getSortOrder(), command.sortOrder());
        }
        banner.update(command.imageUrl(), command.linkUrl(), command.sortOrder());
        return BannerMapper.toInfo(bannerRepository.save(banner));
    }

    // 배너를 oldSortOrder에서 newSortOrder로 옮길 때, 그 사이 구간에 있는 다른 배너들을
    // 한 칸씩 밀거나 당겨 순번이 중복되지 않도록 한다. (움직이는 배너 자신은 제외)
    private void shiftForMove(String excludeId, int oldSortOrder, int newSortOrder) {
        Instant now = Instant.now();
        if (newSortOrder < oldSortOrder) {
            // 앞으로 당김 — [newSortOrder, oldSortOrder) 구간의 다른 배너를 한 칸씩 뒤로 민다.
            bannerRepository.shiftRangeForward(excludeId, newSortOrder, oldSortOrder, now);
        } else {
            // 뒤로 밀림 — (oldSortOrder, newSortOrder] 구간의 다른 배너를 한 칸씩 앞으로 당긴다.
            bannerRepository.shiftRangeBackward(excludeId, oldSortOrder, newSortOrder, now);
        }
    }

    @Override
    @Transactional
    public void deleteBanner(String bannerId) {
        Banner banner = findBannerById(bannerId);
        banner.delete();
        bannerRepository.save(banner);
    }

    private Banner findBannerById(String bannerId) {
        return bannerRepository.findById(bannerId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.BANNER_NOT_FOUND, bannerId));
    }
}
