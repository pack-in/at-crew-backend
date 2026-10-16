package com.atcrew.portfolio;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 공유 포트폴리오 공개 주소 {@code /{handle}/portfolio/{code}} 조회와, 기존 공유 열람·본인 응답의 주소 조립 필드 검증
 * (docs/design/member-handle-design.md §2.2, §3.4).
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class PortfolioPublicAddressTests {

    @Autowired
    PortfolioServiceImpl portfolioService;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PortfolioRepository portfolioRepository;

    // 비인증 공유 목록은 처리 완료된 작품만 노출하므로(§5.4) Worker webhook 경로로 READY까지 올린다.
    @Autowired
    MediaCallbackService mediaCallbackService;

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

    // 옛 공유 링크 slug를 붙이는 데 쓴다 — 신규 발급이 중단돼 테스트가 직접 넣는다.
    @Autowired
    JdbcTemplate jdbcTemplate;

    // ─── 공개 주소 조회 ───────────────────────────────────────────

    @Test
    void 현재_handle과_코드로_공유_포트폴리오와_작품_목록을_조회한다() {
        String ownerId = registerProMember();
        ArtworkInfo artwork = uploadReadyArtwork(ownerId);
        PortfolioInfo created = portfolioService.createShared(
                ownerId, "공유", ReflectionType.LIVE, List.of(artwork.id()));
        String handle = handleOf(ownerId);

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolioByCode(handle, created.publicCode());

        assertThat(shared.id()).isEqualTo(created.id());
        assertThat(shared.ownerHandle()).isEqualTo(handle);
        assertThat(shared.publicCode()).isEqualTo(created.publicCode()).matches("^[a-z0-9]{6}$");
        // 카드는 원본 상세 주소(/{ownerHandle}/artwork/{publicCode}) 조립용 코드를 담는다.
        assertThat(portfolioService.getSharedPortfolioArtworksByCode(handle, created.publicCode(), null, 20, null)
                .items())
                .extracting(PortfolioArtworkCardInfo::artworkId, PortfolioArtworkCardInfo::publicCode)
                .containsExactly(tuple(artwork.id(), artwork.publicCode()));
    }

    @Test
    void 옛_handle로_조회해도_응답에는_현재_handle이_담긴다() {
        String ownerId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(ownerId, "공유", ReflectionType.LIVE, List.of());
        String oldHandle = handleOf(ownerId);
        String newHandle = changeHandle(ownerId);

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolioByCode(oldHandle, created.publicCode());

        assertThat(shared.id()).isEqualTo(created.id());
        assertThat(shared.ownerHandle()).isEqualTo(newHandle);
    }

    @Test
    void 탈퇴한_소유자의_handle은_410_MEMBER_WITHDRAWN() {
        String ownerId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(ownerId, "공유", ReflectionType.LIVE, List.of());
        String handle = handleOf(ownerId);
        memberService.deactivate(ownerId);

        assertThatThrownBy(() -> portfolioService.getSharedPortfolioByCode(handle, created.publicCode()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("MEMBER_WITHDRAWN");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.GONE);
                });
        assertThatThrownBy(() -> portfolioService.getSharedPortfolioArtworksByCode(
                handle, created.publicCode(), null, 20, null))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("MEMBER_WITHDRAWN");
    }

    @Test
    void 쓰인_적_없는_handle은_404_MEMBER_NOT_FOUND() {
        assertThatThrownBy(() -> portfolioService.getSharedPortfolioByCode("nobody_here", "abc123"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void 그_소유자에게_없는_코드와_다른_작가의_코드는_404_PORTFOLIO_NOT_FOUND() {
        String ownerA = registerProMember();
        String ownerB = registerProMember();
        PortfolioInfo ofB = portfolioService.createShared(ownerB, "B의 공유", ReflectionType.LIVE, List.of());

        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolioByCode(handleOf(ownerB), "zzzzzz"));
        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolioByCode(handleOf(ownerA), ofB.publicCode()));
        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolioArtworksByCode(
                handleOf(ownerA), ofB.publicCode(), null, 20, null));
    }

    // 작가 페이지의 주소는 /{handle}이라 코드가 없다 — 코드 경로로는 어떤 값으로도 열리지 않는다.
    @Test
    void 작가_페이지는_코드가_없어_코드_경로로_열리지_않는다() {
        String ownerId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(ownerId).getFirst().id();
        String handle = handleOf(ownerId);

        PortfolioSharedInfo artistPage = portfolioService.getSharedPortfolio(handle);
        assertThat(artistPage.id()).isEqualTo(artistPageId);
        assertThat(artistPage.publicCode()).isNull();
        assertThat(artistPage.ownerHandle()).isEqualTo(handle);
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getPublicCode()).isNull();

        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolioByCode(handle, "abc123"));
    }

    @Test
    void 고정형_스냅샷_상세를_코드_경로로_조회하고_최신_반영형에서는_404() {
        String ownerId = registerProMember();
        ArtworkInfo artwork = uploadReadyArtwork(ownerId);
        PortfolioInfo snapshot = portfolioService.createShared(
                ownerId, "고정형", ReflectionType.SNAPSHOT, List.of(artwork.id()));
        PortfolioInfo live = portfolioService.createShared(ownerId, "최신", ReflectionType.LIVE, List.of(artwork.id()));
        String handle = handleOf(ownerId);
        PortfolioArtworkCardInfo card = portfolioService.getSharedPortfolioArtworksByCode(
                handle, snapshot.publicCode(), null, 20, null).items().getFirst();
        // 고정형 카드는 원본 주소를 노출하지 않는다.
        assertThat(card.publicCode()).isNull();
        assertThat(card.artworkId()).isNull();

        PortfolioSnapshotDetailInfo detail = portfolioService.getSharedSnapshotDetailByCode(
                handle, snapshot.publicCode(), card.snapshotId());

        assertThat(detail.snapshotId()).isEqualTo(card.snapshotId());
        assertPortfolioNotFound(() -> portfolioService.getSharedSnapshotDetailByCode(
                handle, live.publicCode(), card.snapshotId()));
    }

    // ─── 기존 공유 열람(/shared/{identifier})·본인 응답 ───────────────

    @Test
    void 옛_slug로_열면_새_주소_조립_정보가_담긴다() {
        String ownerId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(ownerId, "공유", ReflectionType.LIVE, List.of());
        String slug = legacySlug(created.id());

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolio(slug);

        assertThat(shared.id()).isEqualTo(created.id());
        assertThat(shared.ownerHandle()).isEqualTo(handleOf(ownerId));
        assertThat(shared.publicCode()).isEqualTo(created.publicCode());
    }

    // 전환기 회귀: 운영 FE는 shareSlug ?? id로 /portfolio/{식별자} 링크를 만든다. 새 공유 포트폴리오에 slug가 없으면
    // 그 링크가 UUID가 돼 404였다(member-handle-design §3.3).
    @Test
    void 새로_만든_공유_포트폴리오는_발급된_slug로_열린다() {
        String ownerId = registerProMember();
        PortfolioInfo live = portfolioService.createShared(ownerId, "최신", ReflectionType.LIVE, List.of());
        PortfolioInfo snapshot = portfolioService.createShared(ownerId, "고정", ReflectionType.SNAPSHOT, List.of());

        assertThat(portfolioService.getSharedPortfolio(live.shareSlug()).id()).isEqualTo(live.id());
        assertThat(portfolioService.getSharedPortfolio(snapshot.shareSlug()).id()).isEqualTo(snapshot.id());
        assertThat(live.shareSlug()).isNotEqualTo(snapshot.shareSlug());
    }

    @Test
    void 옛_handle을_식별자로_열면_작가_페이지와_현재_handle이_담긴다() {
        String ownerId = registerMember();
        String oldHandle = handleOf(ownerId);
        String newHandle = changeHandle(ownerId);

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolio(oldHandle);

        assertThat(shared.kind()).isEqualTo(PortfolioKind.ARTIST_PAGE);
        assertThat(shared.ownerHandle()).isEqualTo(newHandle);
        assertThat(shared.publicCode()).isNull();
        assertThat(portfolioService.getSharedPortfolioArtworks(oldHandle, null, 20).items()).isEmpty();
    }

    // findByShareSlug(null)은 share_slug IS NULL 다건으로 500이 난다 — 빈 식별자는 조회 전에 404로 끊는다.
    @Test
    void 빈_식별자는_slug_조회_전에_404로_끝난다() {
        String ownerId = registerProMember();
        // slug 없는 행이 둘 이상이어야 null 조회가 다건이 된다 — 전환기에는 발급되므로 비워서 만든다.
        for (String title : List.of("slug 없는 공유 1", "slug 없는 공유 2")) {
            String id = portfolioService.createShared(ownerId, title, ReflectionType.LIVE, List.of()).id();
            jdbcTemplate.update("UPDATE portfolios SET share_slug = NULL WHERE id = ?", id);
        }

        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolio(null));
        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolio(" "));
        assertPortfolioNotFound(() -> portfolioService.getSharedPortfolioArtworks(null, null, 20));
        assertPortfolioNotFound(() -> portfolioService.getSharedSnapshotDetail(null, UUID.randomUUID().toString()));
    }

    @Test
    void 본인_응답에_현재_handle과_코드가_담기고_작가_페이지는_코드가_null() {
        String ownerId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(ownerId, "공유", ReflectionType.LIVE, List.of());
        String handle = handleOf(ownerId);

        assertThat(created.ownerHandle()).isEqualTo(handle);
        assertThat(created.publicCode()).isNotNull();
        assertThat(portfolioService.getPortfolio(ownerId, created.id()).ownerHandle()).isEqualTo(handle);

        List<PortfolioSummaryInfo> mine = portfolioService.getMyPortfolios(ownerId, null, null, null, null, 20).items();
        assertThat(mine).extracting(PortfolioSummaryInfo::kind, PortfolioSummaryInfo::ownerHandle,
                        PortfolioSummaryInfo::publicCode)
                .containsExactlyInAnyOrder(
                        tuple(PortfolioKind.SHARED, handle, created.publicCode()),
                        tuple(PortfolioKind.ARTIST_PAGE, handle, null));
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────

    private void assertPortfolioNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).getCode()).isEqualTo("PORTFOLIO_NOT_FOUND");
                    assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    private String changeHandle(String memberId) {
        String newHandle = "nw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        memberService.changeHandle(memberId, newHandle);
        return newHandle;
    }

    private String legacySlug(String portfolioId) {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        jdbcTemplate.update("UPDATE portfolios SET share_slug = ? WHERE id = ?", slug, portfolioId);
        return slug;
    }

    private ArtworkInfo uploadReadyArtwork(String memberId) {
        String name = UUID.randomUUID().toString();
        String key = "raw/" + keySigner.sign(memberId, name) + "/" + name + ".png";
        ArtworkInfo uploaded = artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(key), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));
        mediaCallbackService.process(MediaOwnerType.ARTWORK, uploaded.id(), key,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        while (artworkService.getArtworkStatus(memberId, uploaded.id()) != ArtworkStatus.READY) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("READY 전환 대기 시간 초과");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        return artworkService.getArtwork(uploaded.id(), memberId);
    }

    private String handleOf(String memberId) {
        return memberService.findById(memberId).handle();
    }

    private String registerMember() {
        return memberService.register(
                "pfa-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "pa" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
    }

    private String registerProMember() {
        String memberId = registerMember();
        BillingTestSupport.grantProPlan(subscriptionRepository, memberId);
        return memberId;
    }
}
