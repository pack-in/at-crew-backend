package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.Language;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.PortfolioSelectableInfo;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItem;
import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정합성 재계산·보정 배치 검증 (docs/design/portfolio-module-design.md §1.2, §5.5).
 *
 * <p>배치와 재계산 컴포넌트가 package-private이라 같은 패키지에 둔다. 비동기 이벤트 수신 경로는
 * {@code PortfolioServiceTests}가 담당하고, 여기서는 재계산 규칙 자체와 이벤트가 유실됐을 때 배치가
 * 회수하는지를 동기 호출로 확인한다 — 그래서 어긋난 상태를 리포지토리로 직접 만든다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class PortfolioMembershipReconcileTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    PortfolioMembershipReconcileScheduler scheduler;

    @Autowired
    PortfolioMembershipReconciler reconciler;

    @Autowired
    PortfolioServiceImpl portfolioService;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    PortfolioItemRepository portfolioItemRepository;

    @Autowired
    ArtworkRepository artworkRepository;

    // 공개 범위 변경 전제인 READY 전환을 실제 이미지 처리 경로로 만든다(uploadReadyArtwork).
    @Autowired
    MediaCallbackService mediaCallbackService;

    // 탈퇴·운영 차단은 관리자 API 없이 DB 직접 UPDATE로 이뤄지므로 테스트도 같은 경로를 쓴다.
    @Autowired
    JdbcTemplate jdbcTemplate;

    // 작품 피드 공개 OFF는 노출 제외 트리거가 아니다 — 라이브 포트폴리오 소속 자체가 유효한 공개 위치라
    // 구성 행도 개수도 그대로여야 한다(마이페이지_작가-R38·R39).
    @Test
    void 재계산은_비공개로_바뀐_작품을_구성에서_빼지_않는다() {
        String memberId = registerMember();
        String privateArtworkId = uploadReadyArtwork(memberId);
        String publicArtworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(privateArtworkId, publicArtworkId));

        // 노출 위치 재선언은 포트폴리오 목록을 그대로 유지한 채 피드 공개만 끈다(업로드-R09).
        artworkService.updatePublication(memberId, privateArtworkId, false, List.of(artistPageId));
        reconciler.reconcileArtwork(privateArtworkId);

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(privateArtworkId, publicArtworkId);
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isEqualTo(2);
        assertThat(artworkRepository.findById(privateArtworkId).orElseThrow().isPortfolioIncluded()).isTrue();
    }

    @Test
    void 보정_배치는_고아_구성_행과_편입_여부_개수_불일치를_바로잡는다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));

        // 이벤트 유실 상태를 만든다 — 영구 삭제된 원본을 가리키는 구성 행, 해제된 편입 여부, 어긋난 개수 캐시.
        portfolioItemRepository.save(PortfolioItem.of(artistPageId, UUID.randomUUID().toString(), 1));
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        artwork.updatePortfolioInclusion(false);
        artworkRepository.save(artwork);
        Portfolio portfolio = portfolioRepository.findById(artistPageId).orElseThrow();
        portfolio.updateItemCount(5);
        portfolioRepository.save(portfolio);

        scheduler.reconcileMemberships();

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(artworkId);
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isEqualTo(1);
        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isTrue();
    }

    // "업데이트순" 정렬 기준은 [수정하기] 시각이라 자동 재계산이 건드리면 안 된다(마이페이지_작가-R37).
    @Test
    void 재계산은_업데이트순_정렬_기준을_건드리지_않는다() {
        String memberId = registerMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(trashedArtworkId));
        Instant lastEditedAt = portfolioRepository.findById(artistPageId).orElseThrow().getLastEditedAt();

        artworkService.deleteArtwork(memberId, trashedArtworkId);
        reconciler.reconcileArtwork(trashedArtworkId);

        Portfolio reconciled = portfolioRepository.findById(artistPageId).orElseThrow();
        assertThat(reconciled.getItemCount()).isZero();
        assertThat(reconciled.getLastEditedAt()).isEqualTo(lastEditedAt);
    }

    // 휴지통 작품은 구성 행을 유지한 채 열람 가능 개수에서만 빠진다(§5.4) — 배치도 같은 규칙을 따라야 한다.
    @Test
    void 보정_배치는_휴지통_작품의_구성_행을_지우지_않는다() {
        String memberId = registerMember();
        String keptArtworkId = uploadArtwork(memberId);
        String trashedArtworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(keptArtworkId, trashedArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);

        scheduler.reconcileMemberships();

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(keptArtworkId, trashedArtworkId);
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isEqualTo(1);
    }

    // 고아 행 제거(portfolio_items)와 개수 재계산(portfolios)이 한 번의 보정에서 함께 일어나는 경로다 —
    // 쓰기 순서를 items → portfolios로 고정하기 위해 고아 행 삭제를 먼저 flush하도록 바꿨다(§8.9 결함 E).
    @Test
    void 보정_배치는_고아_행_제거와_휴지통_개수_재계산을_함께_반영한다() {
        String memberId = registerMember();
        String keptArtworkId = uploadArtwork(memberId);
        String trashedArtworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(keptArtworkId, trashedArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        // 영구 삭제 이벤트를 놓쳐 원본 없는 구성 행이 남은 상태를 만든다.
        portfolioItemRepository.save(PortfolioItem.of(artistPageId, UUID.randomUUID().toString(), 2));

        scheduler.reconcileMemberships();

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(keptArtworkId, trashedArtworkId);
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isEqualTo(1);
        assertThat(artworkRepository.findById(keptArtworkId).orElseThrow().isPortfolioIncluded()).isTrue();
    }

    // 열람이 막힌 포트폴리오(탈퇴·운영 차단)는 유효한 공개 위치가 아니다 — 보정 배치가 편입 여부를
    // 되살리면 탈퇴 처리에서 해제한 값이 6시간 만에 뒤집혀 탈퇴 회원 작품이 다시 열린다(§5.2, §5.4).
    @Test
    void 보정_배치는_차단된_포트폴리오의_편입_여부를_되살리지_않는다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = artistPageId(memberId);
        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));
        // 탈퇴 처리 결과(포트폴리오 차단 + 편입 해제)를 저장 상태로 재현한다 — 차단은 비동기 구독이라
        // 여기서는 같은 SQL로 직접 만든다(docs/operations/moderation-block.md와 동일한 UPDATE).
        jdbcTemplate.update("UPDATE portfolios SET blocked_at = UTC_TIMESTAMP(6) WHERE id = ?", artistPageId);
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        artwork.updatePortfolioInclusion(false);
        artworkRepository.save(artwork);

        scheduler.reconcileMemberships();

        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    private String registerMember() {
        return memberService.register(
                "rc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "rc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가").id();
    }

    private String artistPageId(String memberId) {
        return portfolioService.getSelectablePortfolios(memberId).stream()
                .map(PortfolioSelectableInfo::id)
                .findFirst()
                .orElseThrow();
    }

    private String uploadArtwork(String memberId) {
        return uploadArtwork(memberId, "raw/" + UUID.randomUUID() + ".png");
    }

    private String uploadArtwork(String memberId, String imageKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of(Genre.FANTASY), List.of("태그"),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of("clip studio"),
                new WorkDuration(1, 1, 1, 1), 1, List.of(), List.of())).id();
    }

    /** 공개 범위 변경은 READY 상태를 요구하므로 media webhook 경로로 READY까지 올린다(PortfolioServiceTests와 동일). */
    private String uploadReadyArtwork(String memberId) {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadArtwork(memberId, imageKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);
        return artworkId;
    }

    /** artwork 리스너는 @ApplicationModuleListener(비동기)라 READY 반영까지 폴링한다. */
    private void awaitReady(String memberId, String artworkId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (artworkService.getArtworkStatus(memberId, artworkId) == ArtworkStatus.READY) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("READY 전환 대기 시간 초과");
    }
}
