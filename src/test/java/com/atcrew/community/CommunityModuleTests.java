package com.atcrew.community;

import com.atcrew.TestMongoConfig;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// community는 artwork·member 모두에 의존(CommunityController)하므로 DIRECT_DEPENDENCIES로는
// 그 모듈들의 추이적 의존성(PasswordEncoder 등 common.security 빈)까지 부트스트랩되지 않는다.
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
@Import(TestMongoConfig.class)
class CommunityModuleTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    BannerService bannerService;

    @Autowired
    MemberService memberService;

    @Test
    void 배너_등록_시_순번_미지정이면_마지막_순번_자동_부여() {
        String memberId = memberService.register(
                "banner-owner-1@atcrew.com", "bannerowner1", "배너주인1", CreatorRole.WEBTOON).id();

        BannerInfo first = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/1.png", "https://example.com/1", null));
        BannerInfo second = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/2.png", "https://example.com/2", null));

        assertThat(second.sortOrder()).isEqualTo(first.sortOrder() + 1);
    }

    @Test
    void 배너_등록_시_순번_지정하면_기존_배너들이_밀림() {
        String memberId = memberService.register(
                "banner-owner-2@atcrew.com", "bannerowner2", "배너주인2", CreatorRole.WEBTOON).id();
        BannerInfo first = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/1.png", "https://example.com/1", null));

        BannerInfo inserted = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/0.png", "https://example.com/0", first.sortOrder()));

        BannerInfo pushedFirst = bannerService.getActiveBanners().stream()
                .filter(b -> b.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(inserted.sortOrder()).isEqualTo(first.sortOrder());
        assertThat(pushedFirst.sortOrder()).isEqualTo(first.sortOrder() + 1);
    }

    @Test
    void 배너_삭제_후_활성_목록에서_제외() {
        String memberId = memberService.register(
                "banner-owner-3@atcrew.com", "bannerowner3", "배너주인3", CreatorRole.WEBTOON).id();
        BannerInfo banner = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/3.png", "https://example.com/3", null));

        bannerService.deleteBanner(banner.id());

        assertThat(bannerService.getActiveBanners()).extracting(BannerInfo::id).doesNotContain(banner.id());
    }

    @Test
    void 배너_수정() {
        String memberId = memberService.register(
                "banner-owner-4@atcrew.com", "bannerowner4", "배너주인4", CreatorRole.WEBTOON).id();
        BannerInfo banner = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/4.png", "https://example.com/4", null));

        BannerInfo updated = bannerService.updateBanner(banner.id(),
                new UpdateBannerCommand("https://img.example/4-new.png", null, null));

        assertThat(updated.imageUrl()).isEqualTo("https://img.example/4-new.png");
        assertThat(updated.linkUrl()).isEqualTo(banner.linkUrl());
    }

    // ─── updateBanner sortOrder 이동 ─────────────────────────────────────

    @Test
    void 배너_수정으로_앞으로_당기면_그_사이_배너들이_뒤로_밀림() {
        String memberId = memberService.register(
                "banner-owner-5@atcrew.com", "bannerowner5", "배너주인5", CreatorRole.WEBTOON).id();
        BannerInfo a = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/a.png", "https://example.com/a", null));
        BannerInfo b = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/b.png", "https://example.com/b", null));
        BannerInfo c = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/c.png", "https://example.com/c", null));
        // 초기 순번: a=0, b=1, c=2

        bannerService.updateBanner(c.id(), new UpdateBannerCommand(null, null, a.sortOrder()));
        // c를 맨 앞으로 이동 → 기대: c=0, a=1, b=2

        var banners = bannerService.getActiveBanners();
        assertThat(findSortOrder(banners, c.id())).isEqualTo(a.sortOrder());
        assertThat(findSortOrder(banners, a.id())).isEqualTo(a.sortOrder() + 1);
        assertThat(findSortOrder(banners, b.id())).isEqualTo(a.sortOrder() + 2);
        assertThat(banners).extracting(BannerInfo::sortOrder).doesNotHaveDuplicates();
    }

    @Test
    void 배너_수정으로_뒤로_밀면_그_사이_배너들이_앞으로_당겨짐() {
        String memberId = memberService.register(
                "banner-owner-6@atcrew.com", "bannerowner6", "배너주인6", CreatorRole.WEBTOON).id();
        BannerInfo a = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/a.png", "https://example.com/a", null));
        BannerInfo b = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/b.png", "https://example.com/b", null));
        BannerInfo c = bannerService.createBanner(new CreateBannerCommand(
                memberId, "https://img.example/c.png", "https://example.com/c", null));
        // 초기 순번: a=0, b=1, c=2

        bannerService.updateBanner(a.id(), new UpdateBannerCommand(null, null, c.sortOrder()));
        // a를 맨 뒤로 이동 → 기대: b=0, c=1, a=2

        var banners = bannerService.getActiveBanners();
        assertThat(findSortOrder(banners, b.id())).isEqualTo(a.sortOrder());
        assertThat(findSortOrder(banners, c.id())).isEqualTo(a.sortOrder() + 1);
        assertThat(findSortOrder(banners, a.id())).isEqualTo(a.sortOrder() + 2);
        assertThat(banners).extracting(BannerInfo::sortOrder).doesNotHaveDuplicates();
    }

    private int findSortOrder(List<BannerInfo> banners, String bannerId) {
        return banners.stream().filter(b -> b.id().equals(bannerId)).findFirst().orElseThrow().sortOrder();
    }
}
