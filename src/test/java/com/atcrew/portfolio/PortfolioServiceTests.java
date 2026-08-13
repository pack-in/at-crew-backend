package com.atcrew.portfolio;

import com.atcrew.SharedContainersConfig;
import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.billing.Plan;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.exception.BillingException;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.test.ApplicationModuleTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * portfolio 코어 CRUD 검증 (docs/design/portfolio-module-design.md §4, §5).
 *
 * <p>플랜 승급은 billing 웹훅이 아직 없으므로 구독 행을 직접 만들어 시뮬레이션한다(BillingModuleTests와 동일).
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@ExtendWith(DatabaseCleanupExtension.class)
@ImportTestcontainers(SharedContainersConfig.class)
class PortfolioServiceTests {

    @Autowired
    PortfolioServiceImpl portfolioService;

    @Autowired
    ArtworkService artworkService;

    @Autowired
    MemberService memberService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    // 편입 여부(artworks.portfolio_included)가 실제로 반영됐는지는 artwork 저장 상태로 직접 확인한다(§1.2).
    @Autowired
    ArtworkRepository artworkRepository;

    // 고정형 스냅샷 행(payload_json 포함)을 직접 확인한다(§2.3).
    @Autowired
    PortfolioItemSnapshotRepository portfolioItemSnapshotRepository;

    // 탈퇴 이벤트가 실제로 blocked_at을 찍었는지 저장 상태로 직접 확인한다(§5.2).
    @Autowired
    PortfolioRepository portfolioRepository;

    @Autowired
    JsonMapper jsonMapper;

    // 공개 범위 변경 전제인 READY 전환을 실제 이미지 처리 경로로 만든다(uploadReadyArtwork).
    @Autowired
    MediaCallbackService mediaCallbackService;

    @Test
    void 작가_페이지는_최초_조회_시_한_번만_생성된다() {
        String memberId = registerMember();

        List<PortfolioSelectableInfo> first = portfolioService.getSelectablePortfolios(memberId);
        List<PortfolioSelectableInfo> second = portfolioService.getSelectablePortfolios(memberId);

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().kind()).isEqualTo(PortfolioKind.ARTIST_PAGE);
        assertThat(first.getFirst().title()).isNull();
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().id()).isEqualTo(first.getFirst().id());
        assertThat(portfolioService.getMyPortfolios(memberId, null, null, null, null, 20).items())
                .extracting(PortfolioSummaryInfo::id)
                .containsExactly(first.getFirst().id());
    }

    @Test
    void 스타터_계정은_공유_포트폴리오를_만들_수_없다() {
        String memberId = registerMember();

        assertThatThrownBy(() -> portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of()))
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 프로_계정은_공유_포트폴리오를_생성하고_공유_슬러그를_받는다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);

        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(artworkId));

        assertThat(created.kind()).isEqualTo(PortfolioKind.SHARED);
        assertThat(created.reflectionType()).isEqualTo(ReflectionType.LIVE);
        assertThat(created.shareSlug()).isNotBlank().hasSize(22);
        assertThat(created.itemCount()).isEqualTo(1);
        assertThat(created.artworks()).extracting(PortfolioArtworkCardInfo::artworkId).containsExactly(artworkId);
        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isTrue();
    }

    @Test
    void 스타터_계정은_고정형_포트폴리오를_만들_수_없다() {
        String memberId = registerMember();

        assertThatThrownBy(() -> portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of()))
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 프로_계정은_고정형_포트폴리오를_생성하고_생성_시점_구성을_돌려받는다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);

        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));

        assertThat(created.reflectionType()).isEqualTo(ReflectionType.SNAPSHOT);
        assertThat(created.shareSlug()).isNotBlank().hasSize(22);
        assertThat(created.itemCount()).isEqualTo(1);
        assertThat(created.artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId, PortfolioArtworkCardInfo::title)
                .containsExactly(tuple(artworkId, "작품"));
        assertThat(portfolioService.getPortfolio(memberId, created.id()).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);

        // 상세 본문은 payload_json 1컬럼에 담긴다(§2.3) — JSON 컬럼은 저장 시 정규화되므로 원문 비교가 아니라
        // JsonMapper로 역직렬화해서 확인한다.
        PortfolioItemSnapshot snapshot =
                portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(created.id()).getFirst();
        JsonNode payload = jsonMapper.readTree(snapshot.getPayloadJson());
        assertThat(payload.get("description").asText()).isEqualTo("설명");
        assertThat(payload.get("tags").get(0).asText()).isEqualTo("태그");
    }

    // 고정형의 핵심 정책 — 생성 시점 구성이 얼어붙어 원본 수정·삭제·비공개 전환에 영향받지 않는다(§5.1).
    @Test
    void 고정형은_원본이_바뀌어도_생성_시점_구성을_유지한다() {
        String memberId = registerProMember();
        String renamedArtworkId = uploadArtwork(memberId);
        String trashedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(renamedArtworkId, trashedArtworkId));

        artworkService.updateArtwork(memberId, renamedArtworkId, new UpdateArtworkCommand(
                null, null, null, null, "바뀐 제목", null, null, null,
                null, null, null, null, null, null, null, null, null));
        // 휴지통 이동은 원본을 DELETED + PRIVATE로 바꾼다 — 삭제·비공개 전환을 한 번에 검증한다.
        artworkService.deleteArtwork(memberId, trashedArtworkId);

        // 원본이 실제로 바뀌었는지 먼저 확인한다 — 그래야 아래 불변 검증이 유효하다.
        assertThat(artworkRepository.findById(renamedArtworkId).orElseThrow().getTitle()).isEqualTo("바뀐 제목");
        assertThat(artworkRepository.findById(trashedArtworkId).orElseThrow().getStatus())
                .isEqualTo(ArtworkStatus.DELETED);

        PortfolioInfo reloaded = portfolioService.getPortfolio(memberId, created.id());

        assertThat(reloaded.itemCount()).isEqualTo(2);
        assertThat(reloaded.artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId, PortfolioArtworkCardInfo::title)
                .containsExactly(tuple(renamedArtworkId, "작품"), tuple(trashedArtworkId, "작품"));
        // 원본의 비공개 전환이 스냅샷으로 새면 안 된다 — 스냅샷 카드는 원본을 조회하지 않고 항상 PUBLIC이다.
        assertThat(reloaded.artworks())
                .extracting(PortfolioArtworkCardInfo::visibility)
                .containsOnly(Visibility.PUBLIC);
    }

    // 고정형은 portfolio_items를 쓰지 않으므로 라이브 멤버십(완전 비공개 판정)에 잡히지 않는다(§1.2, §5.4).
    @Test
    void 고정형_생성은_작품의_편입_여부를_바꾸지_않는다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);

        portfolioService.createShared(memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));

        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    @Test
    void 고정형_포트폴리오는_수정할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        String otherArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));

        assertThatThrownBy(() -> portfolioService.updatePortfolio(memberId, created.id(), "새 제목", null))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> portfolioService.addArtworks(memberId, created.id(), List.of(otherArtworkId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> portfolioService.removeArtwork(memberId, created.id(), artworkId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 작품을_추가하고_제거하면_편입_여부가_함께_바뀐다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));

        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isTrue();
        assertThat(portfolioService.getPortfolio(memberId, artistPageId).itemCount()).isEqualTo(1);

        portfolioService.removeArtwork(memberId, artistPageId, artworkId);

        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isFalse();
        assertThat(portfolioService.getPortfolio(memberId, artistPageId).itemCount()).isZero();
    }

    // 구성 교체는 uk_pi_order·uk_pi_pf_artwork 충돌을 피해야 하므로 벌크 DELETE 후 재삽입한다.
    @Test
    void 구성을_통째로_교체하면_빠진_작품의_편입이_해제된다() {
        String memberId = registerMember();
        String keptArtworkId = uploadArtwork(memberId);
        String droppedArtworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(keptArtworkId, droppedArtworkId));

        portfolioService.updatePortfolio(memberId, artistPageId, null, List.of(keptArtworkId));

        assertThat(artworkRepository.findById(keptArtworkId).orElseThrow().isPortfolioIncluded()).isTrue();
        assertThat(artworkRepository.findById(droppedArtworkId).orElseThrow().isPortfolioIncluded()).isFalse();
        assertThat(portfolioService.getPortfolio(memberId, artistPageId).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(keptArtworkId);
    }

    @Test
    void 작가_페이지는_삭제할_수_없다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThatThrownBy(() -> portfolioService.deletePortfolio(memberId, artistPageId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 작가_페이지는_제목을_변경할_수_없다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThatThrownBy(() -> portfolioService.updatePortfolio(memberId, artistPageId, "제목", null))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTIST_PAGE_TITLE_IMMUTABLE");
    }

    @Test
    void 남의_포트폴리오는_조회할_수_없다() {
        String ownerId = registerMember();
        String otherId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(ownerId).getFirst().id();

        assertThatThrownBy(() -> portfolioService.getPortfolio(otherId, artistPageId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_ACCESS_DENIED");
    }

    @Test
    void 남의_작품은_포트폴리오에_담을_수_없다() {
        String memberId = registerMember();
        String otherId = registerMember();
        String othersArtworkId = uploadArtwork(otherId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThatThrownBy(() -> portfolioService.addArtworks(memberId, artistPageId, List.of(othersArtworkId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTWORK_NOT_FOUND");
    }

    // 다운그레이드 후에도 공유 포트폴리오 삭제는 허용한다(§5.5, 요금제-R01).
    @Test
    void 공유_포트폴리오_삭제는_플랜과_무관하게_허용된다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(artworkId));
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByMemberId(memberId).stream().toList());

        portfolioService.deletePortfolio(memberId, created.id());

        assertThat(portfolioService.getMyPortfolios(memberId, PortfolioKind.SHARED, null, null, null, 20).items())
                .isEmpty();
        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    // 복제 자동 선택은 원본의 현재 상태로 판정한다 — 삭제·비공개는 빠지고 개수만 알려준다(§5.3, 마이페이지_작가-R41).
    @Test
    void 복제_원본에서_삭제되거나_비공개인_작품은_자동_선택에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String deletedArtworkId = uploadArtwork(memberId);
        String privateArtworkId = uploadReadyArtwork(memberId);
        String anotherKeptArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(keptArtworkId, deletedArtworkId, privateArtworkId, anotherKeptArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);
        artworkService.updateVisibility(memberId, privateArtworkId, Visibility.PRIVATE);

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.defaultTitle()).isEqualTo("공유 포트폴리오 복사본");
        assertThat(source.selectedArtworkIds()).containsExactly(keptArtworkId, anotherKeptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(2);
    }

    // LINK_ONLY는 "비공개"가 아니므로 자동 선택에 남는다(§5.3).
    @Test
    void 복제_원본의_링크_공개_작품은_자동_선택에_남는다() {
        String memberId = registerProMember();
        String linkOnlyArtworkId = uploadReadyArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(linkOnlyArtworkId));
        artworkService.updateVisibility(memberId, linkOnlyArtworkId, Visibility.LINK_ONLY);

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.selectedArtworkIds()).containsExactly(linkOnlyArtworkId);
        assertThat(source.excludedCount()).isZero();
    }

    // 고정형은 스냅샷이 아니라 원본의 현재 상태로 판정한다 — 복제본은 원본을 다시 담기 때문이다(§5.3).
    @Test
    void 고정형_복제는_스냅샷의_원본_작품을_현재_상태로_다시_판정한다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String deletedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(keptArtworkId, deletedArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.defaultTitle()).isEqualTo("고정형 복사본");
        assertThat(source.selectedArtworkIds()).containsExactly(keptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(1);
        // 원본이 바뀌어도 스냅샷 자체는 그대로다 — 복제 판정이 스냅샷을 건드리지 않는지 함께 확인한다(§5.1).
        assertThat(portfolioService.getPortfolio(memberId, created.id()).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(keptArtworkId, deletedArtworkId);
    }

    // 작가 페이지는 제목이 없고 사용자 이름을 헤더로 쓰므로 기본 제목도 사용자 이름 기준이다(§5.3).
    @Test
    void 작가_페이지_복제의_기본_제목은_사용자_이름_기준이다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, artistPageId);

        assertThat(source.defaultTitle()).isEqualTo("작가 복사본");
        assertThat(source.selectedArtworkIds()).containsExactly(artworkId);
        assertThat(source.excludedCount()).isZero();
    }

    // 자동 선택 0개여도 복제를 진행할 수 있으므로 예외가 아니라 빈 목록으로 응답한다(§5.3).
    @Test
    void 빈_포트폴리오도_복제_원본으로_조회된다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, artistPageId);

        assertThat(source.selectedArtworkIds()).isEmpty();
        assertThat(source.excludedCount()).isZero();
    }

    // === 카드 커버 썸네일 (마이페이지_작가-R39) ===

    @Test
    void 카드_커버는_업로드가_오래된_작품_4개의_썸네일이다() {
        String memberId = registerProMember();
        List<String> artworkIds = List.of(
                uploadArtworkWithThumb(memberId, "thumb-1"),
                uploadArtworkWithThumb(memberId, "thumb-2"),
                uploadArtworkWithThumb(memberId, "thumb-3"),
                uploadArtworkWithThumb(memberId, "thumb-4"),
                uploadArtworkWithThumb(memberId, "thumb-5"));
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, artworkIds);

        PortfolioSummaryInfo summary = findSummary(memberId, created.id());

        assertThat(summary.coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey, PortfolioCoverThumbnailInfo::thumbAdultKey)
                .containsExactly(
                        tuple("thumb-1", "thumb-1-adult"),
                        tuple("thumb-2", "thumb-2-adult"),
                        tuple("thumb-3", "thumb-3-adult"),
                        tuple("thumb-4", "thumb-4-adult"));
    }

    @Test
    void 카드_커버는_작품이_4개_미만이면_있는_만큼만_내려간다() {
        String memberId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(uploadArtworkWithThumb(memberId, "thumb-1"), uploadArtworkWithThumb(memberId, "thumb-2")));

        PortfolioSummaryInfo summary = findSummary(memberId, created.id());

        assertThat(summary.coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey)
                .containsExactly("thumb-1", "thumb-2");
    }

    @Test
    void 빈_포트폴리오의_카드_커버는_빈_배열이다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThat(findSummary(memberId, artistPageId).coverThumbnails()).isEmpty();
    }

    // 최신 반영형은 원본을 조회 시점에 읽으므로 휴지통으로 간 작품은 커버에서도 빠진다.
    @Test
    void 최신_반영형_카드_커버에서_삭제된_작품은_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtworkWithThumb(memberId, "thumb-1");
        String deletedArtworkId = uploadArtworkWithThumb(memberId, "thumb-2");
        String anotherKeptArtworkId = uploadArtworkWithThumb(memberId, "thumb-3");
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(keptArtworkId, deletedArtworkId, anotherKeptArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);

        assertThat(findSummary(memberId, created.id()).coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey)
                .containsExactly("thumb-1", "thumb-3");
    }

    // 고정형 커버는 스냅샷 컬럼 기준이라 원본이 삭제돼도 그대로다(§5.1).
    @Test
    void 고정형_카드_커버는_원본이_삭제돼도_바뀌지_않는다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtworkWithThumb(memberId, "thumb-1");
        String deletedArtworkId = uploadArtworkWithThumb(memberId, "thumb-2");
        PortfolioInfo created = portfolioService.createShared(memberId, "고정형", ReflectionType.SNAPSHOT,
                List.of(keptArtworkId, deletedArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);

        assertThat(findSummary(memberId, created.id()).coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey, PortfolioCoverThumbnailInfo::thumbAdultKey)
                .containsExactly(tuple("thumb-1", "thumb-1-adult"), tuple("thumb-2", "thumb-2-adult"));
    }

    // === 공유 링크 공개 열람 (§4, §5.2) ===

    @Test
    void 공유_슬러그로_최신_반영형_포트폴리오를_열람한다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(artworkId));

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolio(created.shareSlug());

        assertThat(shared.id()).isEqualTo(created.id());
        assertThat(shared.kind()).isEqualTo(PortfolioKind.SHARED);
        assertThat(shared.reflectionType()).isEqualTo(ReflectionType.LIVE);
        assertThat(shared.title()).isEqualTo("공유 포트폴리오");
        assertThat(shared.ownerName()).isEqualTo("작가");
        assertThat(shared.itemCount()).isEqualTo(1);
        assertThat(portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20).items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);
    }

    // 슬러그로 찾지 못하면 작가 페이지 handle로 해석한다(§2.6).
    @Test
    void 작가_handle로_작가_페이지를_열람한다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));
        String handle = memberService.findById(memberId).handle();

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolio(handle);

        assertThat(shared.id()).isEqualTo(artistPageId);
        assertThat(shared.kind()).isEqualTo(PortfolioKind.ARTIST_PAGE);
        assertThat(shared.title()).isNull();
        assertThat(shared.ownerName()).isEqualTo("작가");
        assertThat(portfolioService.getSharedPortfolioArtworks(handle, null, 20).items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);
    }

    // 고정형 헤더의 작성자 이름은 생성 시점에 얼린 값이다(마이페이지_작가-R44) — 최신 반영형과 대비해 확인한다.
    @Test
    void 고정형_공유_열람은_생성_시점_작성자_이름을_유지한다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo snapshot = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        PortfolioInfo live = portfolioService.createShared(
                memberId, "최신 반영형", ReflectionType.LIVE, List.of(artworkId));

        memberService.updateName(memberId, "바뀐 이름");

        assertThat(portfolioService.getSharedPortfolio(snapshot.shareSlug()).ownerName()).isEqualTo("작가");
        assertThat(portfolioService.getSharedPortfolio(live.shareSlug()).ownerName()).isEqualTo("바뀐 이름");
    }

    @Test
    void 존재하지_않는_공유_식별자는_찾을_수_없다() {
        assertThatThrownBy(() -> portfolioService.getSharedPortfolio("no-such-identifier"))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // 탈퇴하면 MemberDeactivatedEvent 구독이 blocked_at을 찍고 공유 링크는 410이 된다(§5.2).
    @Test
    void 탈퇴한_회원의_공유_포트폴리오는_열람할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(artworkId));

        memberService.deactivate(memberId);
        awaitBlocked(created.id());

        assertThatThrownBy(() -> portfolioService.getSharedPortfolio(created.shareSlug()))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_BLOCKED");
        assertThatThrownBy(() -> portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.GONE);
    }

    // 탈퇴 이벤트가 유실돼 blocked_at이 비어 있어도 조회 시점 이중 확인이 차단을 확정한다(§5.2).
    // 소유자 조회 실패 상황을 만들기 위해 존재하지 않는 회원 ID로 포트폴리오 행을 직접 넣는다.
    @Test
    void 소유자를_확인할_수_없는_포트폴리오는_열람_시점에_차단된다() {
        Portfolio orphan = portfolioRepository.save(Portfolio.createShared(
                UUID.randomUUID().toString(), ReflectionType.LIVE, "주인 없는 포트폴리오",
                UUID.randomUUID().toString().replace("-", "").substring(0, 22)));

        assertThatThrownBy(() -> portfolioService.getSharedPortfolio(orphan.getShareSlug()))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_BLOCKED");
        // 410을 던지며 조회 트랜잭션이 롤백돼도 차단은 별도 트랜잭션으로 남아야 한다.
        assertThat(portfolioRepository.findById(orphan.getId()).orElseThrow().getBlockedAt()).isNotNull();
    }

    @Test
    void 공유_작품_목록은_커서로_이어서_조회된다() {
        String memberId = registerProMember();
        String firstArtworkId = uploadArtwork(memberId);
        String secondArtworkId = uploadArtwork(memberId);
        String thirdArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(firstArtworkId, secondArtworkId, thirdArtworkId));

        CursorPage<PortfolioArtworkCardInfo> firstPage =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 2);

        assertThat(firstPage.items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(firstArtworkId, secondArtworkId);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo("1");

        CursorPage<PortfolioArtworkCardInfo> secondPage =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), firstPage.nextCursor(), 2);

        assertThat(secondPage.items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(thirdArtworkId);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    private String registerMember() {
        return memberService.register(
                "pf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@atcrew.com",
                "pf" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "작가", CreatorRole.ILLUSTRATOR).id();
    }

    private String registerProMember() {
        String memberId = registerMember();
        subscriptionRepository.save(Subscription.create(memberId, Plan.PRO_MONTHLY, SubscriptionStatus.ACTIVE));
        return memberId;
    }

    private String uploadArtwork(String memberId) {
        return uploadArtwork(memberId, "raw/" + UUID.randomUUID() + ".png");
    }

    private String uploadArtwork(String memberId, String imageKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of("판타지"), List.of("태그"),
                AgeRating.ALL, Visibility.PUBLIC, List.of("clip studio"),
                new WorkDuration(1, 1, 1, 1), 1, List.of(), List.of())).id();
    }

    /** 커버 썸네일 값을 구분하려면 이미지 처리 완료까지 태워야 하므로 media webhook 경로로 썸네일 키를 지정한다. */
    private String uploadArtworkWithThumb(String memberId, String thumbKey) {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadArtwork(memberId, imageKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                thumbKey, thumbKey + "-adult", "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);
        return artworkId;
    }

    /** 목록 응답에서 대상 포트폴리오 카드 1건을 집어낸다 — 커버 썸네일은 목록 응답에만 담긴다. */
    private PortfolioSummaryInfo findSummary(String memberId, String portfolioId) {
        return portfolioService.getMyPortfolios(memberId, null, null, null, null, 50).items().stream()
                .filter(summary -> summary.id().equals(portfolioId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("포트폴리오를 목록에서 찾지 못했다: " + portfolioId));
    }

    /**
     * 공개 범위 변경(`changeVisibility`)은 READY 상태를 요구하므로 media webhook 경로로 READY까지 올린다
     * (BookmarkModuleTests와 동일한 패턴).
     */
    private String uploadReadyArtwork(String memberId) {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadArtwork(memberId, imageKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);
        return artworkId;
    }

    /** 탈퇴 이벤트 구독도 @ApplicationModuleListener(비동기)라 blocked_at 반영까지 폴링한다. */
    private void awaitBlocked(String portfolioId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (portfolioRepository.findById(portfolioId).orElseThrow().getBlockedAt() != null) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("탈퇴 차단 반영 대기 시간 초과");
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
