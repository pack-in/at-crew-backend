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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final MongoTemplate mongoTemplate;
    private final MemberService memberService;

    BannerServiceImpl(BannerRepository bannerRepository, MongoTemplate mongoTemplate, MemberService memberService) {
        this.bannerRepository = bannerRepository;
        this.mongoTemplate = mongoTemplate;
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
            mongoTemplate.updateMulti(
                    Query.query(Criteria.where("sortOrder").gte(sortOrder)),
                    new Update().inc("sortOrder", 1),
                    Banner.class);
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
        // 순번 재배치(다른 배너 밀기)는 생성 시에만 적용한다 — 수정 시 순번 충돌은 관리자가 직접 정리.
        banner.update(command.imageUrl(), command.linkUrl(), command.sortOrder());
        return BannerMapper.toInfo(bannerRepository.save(banner));
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
