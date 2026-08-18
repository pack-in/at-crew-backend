package com.atcrew.portfolio;

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
import com.atcrew.artwork.internal.domain.artwork.Artwork;
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
import com.atcrew.portfolio.internal.domain.PortfolioItem;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * portfolio 코어 CRUD 검증 (docs/design/portfolio-module-design.md §4, §5).
 *
 * <p>플랜 승급은 billing 웹훅이 아직 없으므로 구독 행을 직접 만들어 시뮬레이션한다(BillingModuleTests와 동일).
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
class PortfolioServiceTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

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

    // 원본 변경 이벤트가 구성 행을 남겼는지·지웠는지 저장 상태로 직접 확인한다(§1.2).
    @Autowired
    PortfolioItemRepository portfolioItemRepository;

    @Autowired
    JsonMapper jsonMapper;

    // 운영 차단은 관리자 API 없이 DB 직접 UPDATE로 이뤄지므로 테스트도 같은 경로를 쓴다
    // (docs/operations/moderation-block.md).
    @Autowired
    JdbcTemplate jdbcTemplate;

    // 공개 범위 변경 전제인 READY 전환을 실제 이미지 처리 경로로 만든다(uploadReadyArtwork).
    @Autowired
    MediaCallbackService mediaCallbackService;

    // 낙관적 락 충돌은 오래된 버전을 쥔 참조를 한 트랜잭션 안에서 만들어 재현하므로 직접 트랜잭션을 연다(§8.9).
    @Autowired
    PlatformTransactionManager transactionManager;

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
        // 고정형 카드는 원본 작품 ID 대신 스냅샷 ID를 노출한다(마이페이지_작가-R39, PH-01).
        assertThat(created.artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId, PortfolioArtworkCardInfo::title)
                .containsExactly(tuple(null, "작품"));
        assertThat(created.artworks().getFirst().snapshotId()).isNotBlank();
        assertThat(portfolioService.getPortfolio(memberId, created.id()).artworks())
                .extracting(PortfolioArtworkCardInfo::snapshotId)
                .containsExactly(created.artworks().getFirst().snapshotId());

        // 상세 본문은 payload_json 1컬럼에 담긴다(§2.3) — JSON 컬럼은 저장 시 정규화되므로 원문 비교가 아니라
        // JsonMapper로 역직렬화해서 확인한다.
        PortfolioItemSnapshot snapshot =
                portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(created.id()).getFirst();
        // 카드에 노출되는 식별자는 PK가 아니라 별도 발급한 공개 식별자다.
        assertThat(snapshot.getSnapshotPublicId()).isEqualTo(created.artworks().getFirst().snapshotId());
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
                .extracting(PortfolioArtworkCardInfo::snapshotId, PortfolioArtworkCardInfo::title)
                .containsExactly(tuple(created.artworks().get(0).snapshotId(), "작품"),
                        tuple(created.artworks().get(1).snapshotId(), "작품"));
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

    // 포트폴리오에 담는 작품 개수에는 상한이 없다(마이페이지_작가-R37·R38·R46).
    @Test
    void 작품을_100개_넘게_담고_추가할_수_있다() {
        String memberId = registerProMember();
        List<String> artworkIds = IntStream.range(0, 101)
                .mapToObj(i -> uploadArtwork(memberId))
                .toList();
        String addedArtworkId = uploadArtwork(memberId);

        PortfolioInfo created = portfolioService.createShared(
                memberId, "대량 포트폴리오", ReflectionType.LIVE, artworkIds);
        assertThat(created.itemCount()).isEqualTo(101);

        portfolioService.addArtworks(memberId, created.id(), List.of(addedArtworkId));

        assertThat(portfolioService.getPortfolio(memberId, created.id()).itemCount()).isEqualTo(102);
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

    // 복제 자동 선택은 원본의 현재 상태로 판정한다 — 삭제된 작품은 빠지고 개수만 알려준다(§5.3, 마이페이지_작가-R41).
    // 최신 반영형 원본에 담긴 비공개 작품은 포트폴리오 한정 공개라 완전 비공개가 아니므로 남는다(§5.4).
    @Test
    void 복제_원본에서_삭제된_작품은_자동_선택에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String deletedArtworkId = uploadArtwork(memberId);
        String privateArtworkId = uploadReadyArtwork(memberId);
        String anotherKeptArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(keptArtworkId, deletedArtworkId, privateArtworkId, anotherKeptArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);
        // 피드 공개만 끄고 최신 반영형 소속은 그대로 둔다 — 포트폴리오 한정 공개라 완전 비공개가 아니다.
        artworkService.updatePublication(memberId, privateArtworkId, false, List.of(created.id()));

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.defaultTitle()).isEqualTo("공유 포트폴리오 복사본");
        assertThat(source.selectedArtworkIds())
                .containsExactly(keptArtworkId, privateArtworkId, anotherKeptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(1);
    }

    // 제외 기준은 단순 비공개가 아니라 완전 비공개(비공개 + 라이브 포트폴리오 미편입)다(§5.4).
    // 고정형은 편입 여부를 올리지 않으므로(§1.2) 고정형에만 담긴 비공개 작품이 곧 완전 비공개다.
    @Test
    void 복제_원본의_완전_비공개_작품은_자동_선택에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String privateArtworkId = uploadReadyArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "고정형", ReflectionType.SNAPSHOT,
                List.of(keptArtworkId, privateArtworkId));

        // 고정형은 라이브 편입이 아니므로 포트폴리오 목록을 비운 재선언이 곧 완전 비공개다.
        artworkService.updatePublication(memberId, privateArtworkId, false, List.of());

        assertThat(artworkRepository.findById(privateArtworkId).orElseThrow().isPortfolioIncluded()).isFalse();
        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());
        assertThat(source.selectedArtworkIds()).containsExactly(keptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(1);
    }

    // 같은 비공개 작품이라도 다른 라이브 포트폴리오(여기서는 작가 페이지)에 담겨 있으면 열람 가능하므로 남는다(§5.4).
    @Test
    void 다른_라이브_포트폴리오에_담긴_비공개_작품은_복제_자동_선택에_남는다() {
        String memberId = registerProMember();
        String privateArtworkId = uploadReadyArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(privateArtworkId));
        PortfolioInfo created = portfolioService.createShared(memberId, "고정형", ReflectionType.SNAPSHOT,
                List.of(privateArtworkId));

        artworkService.updatePublication(memberId, privateArtworkId, false, List.of(artistPageId));

        assertThat(artworkRepository.findById(privateArtworkId).orElseThrow().isPortfolioIncluded()).isTrue();
        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());
        assertThat(source.selectedArtworkIds()).containsExactly(privateArtworkId);
        assertThat(source.excludedCount()).isZero();
    }

    // 링크 공개는 제3의 공개 상태가 아니라 PRIVATE와 동일 취급이므로, 라이브 포트폴리오에 편입되지 않았으면
    // 완전 비공개로 보고 자동 선택에서 뺀다(마이페이지_작가-R04·R41). 수동 선택은 그대로 허용한다.
    @Test
    void 복제_원본의_링크_공개_작품은_편입되지_않았으면_자동_선택에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String linkOnlyArtworkId = uploadReadyArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(keptArtworkId, linkOnlyArtworkId));

        markLinkOnly(linkOnlyArtworkId);

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.selectedArtworkIds()).containsExactly(keptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(1);
        // 자동 선택에서만 빠질 뿐 사용자가 직접 골라 담는 것은 막지 않는다.
        assertThat(portfolioService.createShared(memberId, "수동 선택", ReflectionType.LIVE,
                List.of(linkOnlyArtworkId)).itemCount()).isEqualTo(1);
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
                .extracting(PortfolioArtworkCardInfo::snapshotId)
                .containsExactly(created.artworks().get(0).snapshotId(), created.artworks().get(1).snapshotId());
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

    // === 업로드 노출 위치 조합 (업로드-R09) ===

    // 조합표 1행 — 피드 공개 ON. 포트폴리오를 고르지 않아도 누구나 열람할 수 있다.
    @Test
    void 피드_공개를_켜고_업로드하면_공개_상태가_된다() {
        String memberId = registerMember();

        String artworkId = uploadWithSelection(memberId, true, List.of());

        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(artwork.isPortfolioIncluded()).isFalse();
    }

    // 조합표 2행 — 피드 공개 OFF + 라이브 포트폴리오 편입. 포트폴리오 한정 공개가 된다.
    @Test
    void 피드_공개를_끄고_포트폴리오를_고르면_포트폴리오_한정_공개가_된다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        String artworkId = uploadWithSelection(memberId, false, List.of(artistPageId));

        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(artwork.isPortfolioIncluded()).isTrue();
        assertThat(portfolioService.getPortfolio(memberId, artistPageId).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);
    }

    // 조합표 3행 — 피드 공개 OFF + 미선택. "완전 비공개"라는 별도 선택지 없이 조합에서 계산된다.
    @Test
    void 피드_공개를_끄고_포트폴리오도_고르지_않으면_완전_비공개가_된다() {
        String memberId = registerMember();

        String artworkId = uploadWithSelection(memberId, false, List.of());

        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(artwork.isPortfolioIncluded()).isFalse();
    }

    // 편입 검증은 업로드 트랜잭션 안에서 동기로 이뤄진다 — 실패하면 작품도 남지 않아야 한다(반쪽 상태 방지).
    @Test
    void 스타터가_업로드에서_공유_포트폴리오를_지정하면_작품까지_롤백된다() {
        String memberId = registerProMember();
        PortfolioInfo shared = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of());
        // 프로에서 스타터로 다운그레이드 — 기존 공유 포트폴리오는 남지만 편입은 프로 전용이다(요금제-R01).
        subscriptionRepository.deleteAll(subscriptionRepository.findByMemberId(memberId).stream().toList());
        int before = artworkService.getMyArtworks(memberId, null, 50).items().size();

        assertThatThrownBy(() -> uploadWithSelection(memberId, false, List.of(shared.id())))
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(artworkService.getMyArtworks(memberId, null, 50).items()).hasSize(before);
        assertThat(portfolioService.getPortfolio(memberId, shared.id()).itemCount()).isZero();
    }

    // 타인 포트폴리오 지정도 같은 경로에서 막힌다 — 존재 여부를 흘리지 않도록 접근 거부로 응답한다.
    @Test
    void 남의_포트폴리오를_지정한_업로드는_거부된다() {
        String memberId = registerMember();
        String otherId = registerMember();
        String othersArtistPageId = portfolioService.getSelectablePortfolios(otherId).getFirst().id();

        assertThatThrownBy(() -> uploadWithSelection(memberId, true, List.of(othersArtistPageId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_ACCESS_DENIED");
    }

    // 고정형은 생성 이후 작품을 추가할 수 없으므로 선택 대상이 아니다(마이페이지_작가-R38).
    @Test
    void 고정형_포트폴리오를_지정한_업로드는_거부된다() {
        String memberId = registerProMember();
        PortfolioInfo snapshot = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of());

        assertThatThrownBy(() -> uploadWithSelection(memberId, true, List.of(snapshot.id())))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // 재선언은 증분이 아니라 전체 목록이다 — 빠진 포트폴리오에서는 제외된다.
    @Test
    void 노출_위치_재선언은_목록에_없는_포트폴리오에서_작품을_뺀다() {
        String memberId = registerProMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        PortfolioInfo shared = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of());
        String artworkId = uploadReadyWithSelection(memberId, true, List.of(artistPageId, shared.id()));

        artworkService.updatePublication(memberId, artworkId, false, List.of(shared.id()));

        assertThat(portfolioService.getPortfolio(memberId, artistPageId).artworks()).isEmpty();
        assertThat(portfolioService.getPortfolio(memberId, shared.id()).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(artwork.isPortfolioIncluded()).isTrue();
    }

    // 업로드 시점에는 PROCESSING 상태에서도 같은 조합을 받으므로(업로드-R09), 업로드 직후의 정정도
    // 이미지 처리 완료를 기다리지 않고 반영돼야 한다.
    @Test
    void 이미지_처리_중에도_노출_위치를_재선언할_수_있다() {
        String memberId = registerProMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        PortfolioInfo shared = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of());
        String artworkId = uploadWithSelection(memberId, true, List.of(artistPageId));

        artworkService.updatePublication(memberId, artworkId, false, List.of(shared.id()));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId)).isEmpty();
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(shared.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(artworkId);
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(artwork.isPortfolioIncluded()).isTrue();
    }

    // 휴지통 작품은 복원이 먼저다 — 처리 중 허용이 삭제된 작품까지 열어주면 안 된다.
    @Test
    void 휴지통_작품은_노출_위치를_재선언할_수_없다() {
        String memberId = registerMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        String artworkId = uploadWithSelection(memberId, true, List.of(artistPageId));
        artworkService.deleteArtwork(memberId, artworkId);
        // 휴지통 이동에 따른 개수 재계산(비동기)이 끝난 뒤에 재선언해야 낙관적 락 충돌과 섞이지 않는다.
        awaitItemCount(artistPageId, 0);

        assertThatThrownBy(() -> artworkService.updatePublication(memberId, artworkId, false, List.of()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTWORK_DELETED");

        // 실패한 재선언은 편입까지 함께 롤백된다.
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(artworkId);
    }

    // === 운영 차단 (마이페이지_작가-R38·R39·R41·R46) ===

    // 차단된 작품은 본인 소유라도 선택 대상이 아니다 — 생성·추가 두 경로 모두에서 막는다(R38·R46).
    @Test
    void 운영_차단된_작품은_포트폴리오에_담을_수_없다() {
        String memberId = registerProMember();
        String blockedArtworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        blockArtwork(blockedArtworkId);

        assertThatThrownBy(() -> portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(blockedArtworkId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTWORK_BLOCKED");
        assertThatThrownBy(() -> portfolioService.addArtworks(memberId, artistPageId, List.of(blockedArtworkId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // 차단된 작품은 자동 선택도 수동 선택도 허용하지 않는다(R41) — 여기서는 자동 선택 제외만 검증한다.
    @Test
    void 운영_차단된_작품은_복제_자동_선택에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String blockedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(keptArtworkId, blockedArtworkId));

        blockArtwork(blockedArtworkId);

        PortfolioDuplicationSourceInfo source = portfolioService.getDuplicationSource(memberId, created.id());

        assertThat(source.selectedArtworkIds()).containsExactly(keptArtworkId);
        assertThat(source.excludedCount()).isEqualTo(1);
    }

    // 최신 반영형은 원본을 조회 시점에 읽으므로 차단된 원본이 목록·커버에서 빠진다(R39).
    @Test
    void 운영_차단된_원본은_최신_반영형_목록과_커버에서_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtworkWithThumb(memberId, "thumb-1");
        String blockedArtworkId = uploadArtworkWithThumb(memberId, "thumb-2");
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(keptArtworkId, blockedArtworkId));

        blockArtwork(blockedArtworkId);

        assertThat(portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20).items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(keptArtworkId);
        assertThat(findSummary(memberId, created.id()).coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey)
                .containsExactly("thumb-1");
    }

    // 차단은 고정형 "현재 상태 고정"보다 우선한다(R39) — 카드·커버·개수·공유 목록에서 모두 빠진다.
    @Test
    void 운영_차단된_스냅샷은_고정형_카드와_커버와_개수에서_모두_빠진다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtworkWithThumb(memberId, "thumb-1");
        String blockedArtworkId = uploadArtworkWithThumb(memberId, "thumb-2");
        PortfolioInfo created = portfolioService.createShared(memberId, "고정형", ReflectionType.SNAPSHOT,
                List.of(keptArtworkId, blockedArtworkId));
        assertThat(created.itemCount()).isEqualTo(2);

        blockSnapshotsOf(blockedArtworkId);

        String keptSnapshotId = created.artworks().getFirst().snapshotId();
        PortfolioInfo reloaded = portfolioService.getPortfolio(memberId, created.id());
        assertThat(reloaded.itemCount()).isEqualTo(1);
        assertThat(reloaded.artworks())
                .extracting(PortfolioArtworkCardInfo::snapshotId)
                .containsExactly(keptSnapshotId);
        assertThat(findSummary(memberId, created.id()).coverThumbnails())
                .extracting(PortfolioCoverThumbnailInfo::thumbKey)
                .containsExactly("thumb-1");
        assertThat(portfolioService.getSharedPortfolio(created.shareSlug()).itemCount()).isEqualTo(1);
        assertThat(portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20).items())
                .extracting(PortfolioArtworkCardInfo::snapshotId)
                .containsExactly(keptSnapshotId);
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
        String artworkId = uploadReadyArtwork(memberId);
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
        String artworkId = uploadReadyArtwork(memberId);
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

    // 작가 페이지는 본인이 포트폴리오 탭을 열 때 lazy 생성되므로, 한 번도 열지 않은 회원은 행 자체가 없다.
    // 그 상태에서도 제3자의 공유 링크 접근은 빈 작가 페이지로 열려야 한다(§2.5).
    @Test
    void 작가_페이지가_없는_회원의_handle도_빈_작가_페이지로_열린다() {
        String memberId = registerMember();
        String handle = memberService.findById(memberId).handle();
        assertThat(portfolioRepository.findByOwnerMemberIdAndKind(memberId, PortfolioKind.ARTIST_PAGE)).isEmpty();

        PortfolioSharedInfo shared = portfolioService.getSharedPortfolio(handle);

        assertThat(shared.kind()).isEqualTo(PortfolioKind.ARTIST_PAGE);
        assertThat(shared.itemCount()).isZero();
        assertThat(portfolioService.getSharedPortfolioArtworks(handle, null, 20).items()).isEmpty();
        // 열람 시점에 만들어진 행은 그대로 남아 본인이 탭을 열었을 때 재사용된다.
        assertThat(portfolioService.getSelectablePortfolios(memberId))
                .extracting(PortfolioSelectableInfo::id)
                .containsExactly(shared.id());
    }

    // 탈퇴 회원은 handle이 클리어되므로 해석 자체가 실패한다 — 작가 페이지가 새로 생기면 안 된다.
    @Test
    void 탈퇴한_회원의_handle로는_작가_페이지가_생성되지_않는다() {
        String memberId = registerMember();
        String handle = memberService.findById(memberId).handle();

        memberService.deactivate(memberId);

        assertThatThrownBy(() -> portfolioService.getSharedPortfolio(handle))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(portfolioRepository.findByOwnerMemberIdAndKind(memberId, PortfolioKind.ARTIST_PAGE)).isEmpty();
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

    // 피드 공개만 끄면 accessFor가 포트폴리오 편입을 근거로 여전히 열람을 허용한다(§5.4 2요소 판정) —
    // 탈퇴 처리에서 편입 여부까지 함께 해제해야 탈퇴 회원 작품이 제3자에게 닫힌다.
    @Test
    void 탈퇴하면_작품의_포트폴리오_편입도_해제된다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(artworkId));
        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isTrue();

        memberService.deactivate(memberId);

        // 탈퇴 구독은 동기 리스너라 트랜잭션 종료 시점에 이미 반영돼 있다.
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(artwork.isPortfolioIncluded()).isFalse();
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

    // === 고정형 스냅샷 상세 (마이페이지_작가-R39·R42) ===

    @Test
    void 스냅샷_상세는_생성_시점_값을_그대로_돌려준다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        String snapshotId = created.artworks().getFirst().snapshotId();

        // 원본을 바꿔도 스냅샷 상세는 생성 시점 값을 유지해야 한다(§5.1).
        artworkService.updateArtwork(memberId, artworkId, new UpdateArtworkCommand(
                null, null, null, null, "바뀐 제목", "바뀐 설명", null, null,
                null, null, null, null, null, null, null, null, null));
        memberService.updateName(memberId, "바뀐 이름");

        PortfolioSnapshotDetailInfo detail =
                portfolioService.getSharedSnapshotDetail(created.shareSlug(), snapshotId);

        assertThat(detail.snapshotId()).isEqualTo(snapshotId);
        assertThat(detail.title()).isEqualTo("작품");
        assertThat(detail.description()).isEqualTo("설명");
        assertThat(detail.tags()).containsExactly("태그");
        assertThat(detail.tools()).containsExactly("clip studio");
        assertThat(detail.genres()).containsExactly("판타지");
        assertThat(detail.roles()).containsExactly(ArtworkRole.LINEART);
        assertThat(detail.images()).hasSize(1);
        assertThat(detail.ageRating()).isEqualTo(AgeRating.ALL);
        assertThat(detail.artworkField()).isEqualTo(ArtworkField.ILLUSTRATION);
        assertThat(detail.sourceCreatedAt()).isNotNull();
        assertThat(detail.ownerName()).isEqualTo("작가");
    }

    // 스냅샷 상세는 고정형 안에서만 존재하는 자원이다 — 최신 반영형에는 없으므로 404다.
    @Test
    void 최신_반영형_포트폴리오에는_스냅샷_상세가_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo snapshot = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        PortfolioInfo live = portfolioService.createShared(
                memberId, "최신 반영형", ReflectionType.LIVE, List.of(artworkId));
        String snapshotId = snapshot.artworks().getFirst().snapshotId();

        assertThatThrownBy(() -> portfolioService.getSharedSnapshotDetail(live.shareSlug(), snapshotId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // 식별자만으로 조회하면 남의 고정형 스냅샷이 열린다 — 포트폴리오와 짝이 맞아야 한다(R39).
    @Test
    void 다른_포트폴리오의_스냅샷_식별자로는_열람할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo first = portfolioService.createShared(
                memberId, "고정형 A", ReflectionType.SNAPSHOT, List.of(artworkId));
        PortfolioInfo second = portfolioService.createShared(
                memberId, "고정형 B", ReflectionType.SNAPSHOT, List.of(artworkId));

        String firstSnapshotId = first.artworks().getFirst().snapshotId();
        assertThat(second.artworks().getFirst().snapshotId()).isNotEqualTo(firstSnapshotId);
        assertThatThrownBy(() -> portfolioService.getSharedSnapshotDetail(second.shareSlug(), firstSnapshotId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_NOT_FOUND");
    }

    // 운영 차단된 스냅샷은 상세 URL로도 열리지 않는다 — 차단이 고정형 설정보다 우선한다(R39).
    @Test
    void 운영_차단된_스냅샷_상세는_열람할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        String snapshotId = created.artworks().getFirst().snapshotId();

        blockSnapshotsOf(artworkId);

        assertThatThrownBy(() -> portfolioService.getSharedSnapshotDetail(created.shareSlug(), snapshotId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // 포트폴리오를 삭제하면 그 포트폴리오의 모든 스냅샷 상세 URL도 즉시 막힌다(R37).
    @Test
    void 삭제된_포트폴리오의_스냅샷_상세는_열람할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        String snapshotId = created.artworks().getFirst().snapshotId();

        portfolioService.deletePortfolio(memberId, created.id());

        assertThatThrownBy(() -> portfolioService.getSharedSnapshotDetail(created.shareSlug(), snapshotId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // 탈퇴 회원의 공유 링크는 스냅샷 상세까지 함께 막힌다(R39·§5.2).
    @Test
    void 탈퇴한_회원의_스냅샷_상세는_열람할_수_없다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        String snapshotId = created.artworks().getFirst().snapshotId();

        memberService.deactivate(memberId);
        awaitBlocked(created.id());

        assertThatThrownBy(() -> portfolioService.getSharedSnapshotDetail(created.shareSlug(), snapshotId))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getStatus())
                .isEqualTo(HttpStatus.GONE);
    }

    // payload_json은 write-once라 필드가 늘어나면 구버전 행이 남는다 — 없는 필드가 있어도 실패하지 않아야 한다.
    @Test
    void 구버전_payload로_저장된_스냅샷도_상세를_돌려준다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "고정형", ReflectionType.SNAPSHOT, List.of(artworkId));
        String snapshotId = created.artworks().getFirst().snapshotId();

        // description·images만 있던 시절의 payload로 되돌린다.
        jdbcTemplate.update(
                "UPDATE portfolio_item_snapshots SET payload_json = ? WHERE snapshot_public_id = ?",
                "{\"description\":\"옛 설명\"}", snapshotId);

        PortfolioSnapshotDetailInfo detail =
                portfolioService.getSharedSnapshotDetail(created.shareSlug(), snapshotId);

        assertThat(detail.description()).isEqualTo("옛 설명");
        assertThat(detail.images()).isEmpty();
        assertThat(detail.tags()).isEmpty();
        assertThat(detail.representativeImageIndex()).isZero();
    }

    @Test
    void 공유_작품_목록은_커서로_이어서_조회된다() {
        String memberId = registerProMember();
        String firstArtworkId = uploadReadyArtwork(memberId);
        String secondArtworkId = uploadReadyArtwork(memberId);
        String thirdArtworkId = uploadReadyArtwork(memberId);
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

    // 휴지통 작품은 조회 시점에 빠지므로 원본 행 개수로 다음 페이지를 판정하면 페이지가 비어 보인다 —
    // 필터를 통과한 카드로 size를 채워야 한다.
    @Test
    void 공유_작품_목록은_페이지_중간의_휴지통_작품을_건너뛰고_size를_채운다() {
        String memberId = registerProMember();
        List<String> artworkIds = List.of(
                uploadReadyArtwork(memberId), uploadReadyArtwork(memberId), uploadReadyArtwork(memberId),
                uploadReadyArtwork(memberId), uploadReadyArtwork(memberId));
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, artworkIds);

        artworkService.deleteArtwork(memberId, artworkIds.get(1));
        artworkService.deleteArtwork(memberId, artworkIds.get(2));

        CursorPage<PortfolioArtworkCardInfo> firstPage =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 2);

        assertThat(firstPage.items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkIds.get(0), artworkIds.get(3));
        assertThat(firstPage.hasNext()).isTrue();

        CursorPage<PortfolioArtworkCardInfo> secondPage =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), firstPage.nextCursor(), 2);

        assertThat(secondPage.items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkIds.get(4));
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 공유_작품_목록의_뒷부분이_전부_휴지통이면_빈_페이지를_내려주지_않는다() {
        String memberId = registerProMember();
        List<String> artworkIds = List.of(uploadReadyArtwork(memberId), uploadReadyArtwork(memberId), uploadReadyArtwork(memberId));
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, artworkIds);

        artworkService.deleteArtwork(memberId, artworkIds.get(1));
        artworkService.deleteArtwork(memberId, artworkIds.get(2));

        CursorPage<PortfolioArtworkCardInfo> page =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 2);

        assertThat(page.items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkIds.getFirst());
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    // 업로드 시 포트폴리오 선택이 즉시 반영되므로 처리 중인 작품이 공유 목록에 잠깐 노출될 수 있었는데,
    // 그 작품의 상세는 제3자에게 열리지 않아 카드만 뜨고 눌러도 안 열리는 불일치였다.
    @Test
    void 처리_중인_작품은_공유_목록에서_빠지고_본인_화면에는_남는다() {
        String memberId = registerProMember();
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of());
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadWithSelection(memberId, true, List.of(created.id()), imageKey);

        assertThat(portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20).items()).isEmpty();
        // 본인 화면에서는 처리 중에도 구성에 담긴 것으로 보인다.
        assertThat(portfolioService.getPortfolio(memberId, created.id()).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);

        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);

        assertThat(portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 20).items())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(artworkId);
    }

    // 휴지통 작품이 대량으로 쌓이면 비인증 요청 한 번이 구성 전체를 훑게 된다 — 스캔 상한에서 끊되
    // 남은 구간은 커서로 이어받게 해야 뒤의 작품이 사라지지 않는다.
    @Test
    void 공유_작품_목록은_스캔_상한에서_끊고_커서로_이어받는다() {
        String memberId = registerProMember();
        // size=1이면 청크가 2행이고 상한이 10청크라 한 요청이 최대 20행만 읽는다 — 그보다 많은 휴지통 행을 둔다.
        List<String> trashedArtworkIds = IntStream.range(0, 22)
                .mapToObj(i -> uploadArtwork(memberId))
                .toList();
        String visibleArtworkId = uploadArtworkWithThumb(memberId, "thumb-scan-limit");
        List<String> allArtworkIds = new ArrayList<>(trashedArtworkIds);
        allArtworkIds.add(visibleArtworkId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, allArtworkIds);
        // 공유 목록은 조회 시점에 원본 상태를 다시 읽으므로 개수 캐시 재계산(비동기)을 기다릴 필요가 없다.
        trashedArtworkIds.forEach(artworkId -> artworkService.deleteArtwork(memberId, artworkId));

        CursorPage<PortfolioArtworkCardInfo> firstPage =
                portfolioService.getSharedPortfolioArtworks(created.shareSlug(), null, 1);

        // 상한에 걸려 카드는 못 채웠지만 남은 구간을 감추지 않는다.
        assertThat(firstPage.items()).isEmpty();
        assertThat(firstPage.hasNext()).isTrue();

        List<String> collected = new ArrayList<>();
        String cursor = firstPage.nextCursor();
        for (int page = 0; page < 10 && cursor != null; page++) {
            CursorPage<PortfolioArtworkCardInfo> next =
                    portfolioService.getSharedPortfolioArtworks(created.shareSlug(), cursor, 1);
            next.items().forEach(card -> collected.add(card.artworkId()));
            cursor = next.nextCursor();
        }
        assertThat(collected).containsExactly(visibleArtworkId);
    }

    // === "업데이트순" 정렬 (마이페이지_작가-R37) ===

    // 정렬 기준은 [수정하기](updatePortfolio) 시각이다 — 작품 추가/제거 API로는 순서가 바뀌면 안 된다.
    @Test
    void 업데이트순은_수정하기로만_바뀐다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        String anotherArtworkId = uploadArtwork(memberId);
        PortfolioInfo older = portfolioService.createShared(
                memberId, "먼저 만든 포트폴리오", ReflectionType.LIVE, List.of(artworkId));
        PortfolioInfo newer = portfolioService.createShared(
                memberId, "나중에 만든 포트폴리오", ReflectionType.LIVE, List.of(artworkId));

        // 작품 추가·제거는 시스템 변경이므로 순서에 영향이 없다.
        portfolioService.addArtworks(memberId, older.id(), List.of(anotherArtworkId));
        portfolioService.removeArtwork(memberId, older.id(), anotherArtworkId);

        assertThat(sharedIdsByUpdated(memberId)).containsExactly(newer.id(), older.id());

        portfolioService.updatePortfolio(memberId, older.id(), "제목만 바꾼다", null);

        assertThat(sharedIdsByUpdated(memberId)).containsExactly(older.id(), newer.id());
    }

    // === 목록 커서 (§8.6) ===

    // DATETIME(6)은 마이크로초까지 저장하는데 커서를 밀리초로 자르면, 같은 밀리초의 뒷부분 행이
    // 통째로 건너뛰어지거나(LATEST/UPDATED) 방금 돌려준 행을 다시 잡아 페이지가 멈춘다(OLDEST).
    @Test
    void 같은_밀리초에_만들어진_포트폴리오도_모든_정렬에서_빠짐없이_조회된다() {
        String memberId = registerProMember();
        List<String> ids = List.of(
                portfolioService.createShared(memberId, "포트폴리오1", ReflectionType.LIVE, List.of()).id(),
                portfolioService.createShared(memberId, "포트폴리오2", ReflectionType.LIVE, List.of()).id(),
                portfolioService.createShared(memberId, "포트폴리오3", ReflectionType.LIVE, List.of()).id());
        // 밀리초는 같고 마이크로초만 다른 세 행.
        setSortTimes(ids.get(0), "2026-08-01 00:00:00.123001");
        setSortTimes(ids.get(1), "2026-08-01 00:00:00.123002");
        setSortTimes(ids.get(2), "2026-08-01 00:00:00.123003");

        for (PortfolioSort sort : PortfolioSort.values()) {
            assertThat(pageThroughShared(memberId, sort))
                    .as("정렬=%s", sort)
                    .containsExactlyInAnyOrderElementsOf(ids);
        }
    }

    // 마이크로초까지 완전히 같은 행은 보조 정렬 키(id)로 갈라야 한다 — 아니면 커서가 가리키는 지점이
    // 유일하지 않아 같은 결과가 반복되거나 일부가 건너뛰어진다.
    @Test
    void 정렬_기준_시각이_완전히_같은_포트폴리오도_모두_조회된다() {
        String memberId = registerProMember();
        List<String> ids = List.of(
                portfolioService.createShared(memberId, "포트폴리오1", ReflectionType.LIVE, List.of()).id(),
                portfolioService.createShared(memberId, "포트폴리오2", ReflectionType.LIVE, List.of()).id(),
                portfolioService.createShared(memberId, "포트폴리오3", ReflectionType.LIVE, List.of()).id());
        ids.forEach(id -> setSortTimes(id, "2026-08-02 00:00:00.500000"));

        for (PortfolioSort sort : PortfolioSort.values()) {
            assertThat(pageThroughShared(memberId, sort))
                    .as("정렬=%s", sort)
                    .containsExactlyInAnyOrderElementsOf(ids);
        }
    }

    // === 원본 변경에 따른 정합성 재계산 (§1.2, §5.5) ===

    // 포트폴리오 API를 거치지 않는 경로(작품 휴지통 이동)라 ArtworkChangedEvent 수신으로만 반영된다.
    @Test
    void 구성_작품이_휴지통으로_가면_개수에서_빠지고_복원하면_되돌아온다() {
        String memberId = registerMember();
        String keptArtworkId = uploadArtwork(memberId);
        String trashedArtworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(keptArtworkId, trashedArtworkId));

        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(artistPageId, 1);

        // 휴지통은 되돌릴 수 있으므로 구성 행 자체는 남긴다 — 목록에서만 빠진다.
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(keptArtworkId, trashedArtworkId);
        assertThat(portfolioService.getPortfolio(memberId, artistPageId).artworks())
                .extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(keptArtworkId);

        artworkService.restoreArtworks(memberId, List.of(trashedArtworkId));
        awaitItemCount(artistPageId, 2);
    }

    // 영구 삭제는 되돌릴 원본이 없으므로 구성 행 자체를 지운다(§1.2).
    @Test
    void 구성_작품이_영구_삭제되면_구성_행까지_정리된다() {
        String memberId = registerMember();
        String keptArtworkId = uploadArtwork(memberId);
        // 영구 삭제는 이미지 키 조합에 null을 허용하지 않아 이미지 처리 완료 상태로 만들어 둔다(ArtworkModuleTests와 동일).
        String deletedArtworkId = uploadArtworkWithThumb(memberId, "thumb-1");
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        portfolioService.addArtworks(memberId, artistPageId, List.of(keptArtworkId, deletedArtworkId));

        artworkService.deleteArtwork(memberId, deletedArtworkId);
        artworkService.permanentlyDeleteArtworks(memberId, List.of(deletedArtworkId));
        // 구성 행 제거와 개수 재계산은 같은 트랜잭션이라 행 기준으로만 기다리면 된다.
        awaitItemArtworkIds(artistPageId, List.of(keptArtworkId));

        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isEqualTo(1);
    }

    // === 구성 교체 시 소속 유지·낙관적 락 (§8.9) ===

    // 휴지통 이동만으로는 소속이 끊기면 안 된다 — 작품 추가 API가 기존 휴지통 항목을 지우면 복원할 자리가 없어진다.
    @Test
    void 작품_추가는_이미_담긴_휴지통_작품의_소속을_유지한다() {
        String memberId = registerProMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        String addedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(trashedArtworkId, keptArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(created.id(), 1);

        portfolioService.addArtworks(memberId, created.id(), List.of(addedArtworkId));

        // 순서는 업로드순 고정이므로 보존 항목도 원래 자리로 돌아온다(§2.2).
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(trashedArtworkId, keptArtworkId, addedArtworkId);
        // 개수는 열람 가능한 작품만 센다 — 소속만 유지하는 휴지통 작품은 빠진다(R39).
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(2);
    }

    // "수정하기"는 휴지통 작품을 선택지로 보여주지 않으므로(R38) 요청 목록에 없다는 이유로 지우면 안 된다.
    @Test
    void 구성_전체_재선택은_이미_담긴_휴지통_작품의_소속을_유지한다() {
        String memberId = registerProMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        String addedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(trashedArtworkId, keptArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(created.id(), 1);

        portfolioService.updatePortfolio(memberId, created.id(), null, List.of(keptArtworkId, addedArtworkId));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(trashedArtworkId, keptArtworkId, addedArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(2);
    }

    // 업로드 편입 재선언(업로드-R09)도 같은 구성 교체 경로를 타므로 같은 정책이 적용돼야 한다.
    @Test
    void 업로드_편입은_이미_담긴_휴지통_작품의_소속을_유지한다() {
        String memberId = registerProMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(trashedArtworkId, keptArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(created.id(), 1);

        String uploadedArtworkId = uploadWithSelection(memberId, true, List.of(created.id()));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(trashedArtworkId, keptArtworkId, uploadedArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(2);
    }

    // 운영 차단된 작품도 원본이 남아 있는 한 소속을 유지한다 — 차단 해제되면 그대로 돌아와야 한다(R38·R39).
    @Test
    void 작품_추가는_이미_담긴_운영_차단_작품의_소속을_유지한다() {
        String memberId = registerProMember();
        String blockedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        String addedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(blockedArtworkId, keptArtworkId));
        blockArtwork(blockedArtworkId);

        portfolioService.addArtworks(memberId, created.id(), List.of(addedArtworkId));

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(blockedArtworkId, keptArtworkId, addedArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(2);
    }

    // 소속 유지는 "명시적 제외"까지 막지는 않는다 — 사용자가 뺀 작품만 빠지고 휴지통 항목은 그대로 남는다.
    @Test
    void 작품_제거는_대상만_빼고_휴지통_작품은_남긴다() {
        String memberId = registerProMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String removedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(trashedArtworkId, removedArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(created.id(), 1);

        portfolioService.removeArtwork(memberId, created.id(), removedArtworkId);

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(trashedArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isZero();
    }

    // 소속 유지는 "요청 목록에 안 적힌 것"에만 적용된다 — 사용자가 [빼기]를 누른 휴지통 작품은 실제로 빠져야
    // 한다(§8.9 결함 D). 보존 규칙이 명시적 제거까지 막으면 204를 받고도 영원히 안 빠진다.
    @Test
    void 휴지통_작품도_명시적_제거_요청이면_구성에서_빠진다() {
        String memberId = registerProMember();
        String trashedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(trashedArtworkId, keptArtworkId));
        artworkService.deleteArtwork(memberId, trashedArtworkId);
        awaitItemCount(created.id(), 1);

        portfolioService.removeArtwork(memberId, created.id(), trashedArtworkId);

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(keptArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(1);
        // 라이브 소속이 끊겼으므로 편입 여부도 함께 해제된다(§5.4).
        assertThat(artworkRepository.findById(trashedArtworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    // 운영 차단 작품도 마찬가지다 — 차단은 노출만 막을 뿐 사용자의 명시적 제거를 막지 않는다(§8.9 결함 D).
    @Test
    void 운영_차단_작품도_명시적_제거_요청이면_구성에서_빠진다() {
        String memberId = registerProMember();
        String blockedArtworkId = uploadArtwork(memberId);
        String keptArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(memberId, "공유 포트폴리오", ReflectionType.LIVE,
                List.of(blockedArtworkId, keptArtworkId));
        blockArtwork(blockedArtworkId);

        portfolioService.removeArtwork(memberId, created.id(), blockedArtworkId);

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(keptArtworkId);
        assertThat(portfolioRepository.findById(created.id()).orElseThrow().getItemCount()).isEqualTo(1);
        assertThat(artworkRepository.findById(blockedArtworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    // 노출 위치 재선언의 해제 경로도 명시적 제거다 — 차단된 작품이라도 목록에서 빠지면 소속이 끊겨야 한다.
    @Test
    void 노출_위치_재선언은_운영_차단된_작품도_목록에_없는_포트폴리오에서_뺀다() {
        String memberId = registerProMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        String artworkId = uploadReadyWithSelection(memberId, true, List.of(artistPageId));
        blockArtwork(artworkId);

        artworkService.updatePublication(memberId, artworkId, true, List.of());

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId)).isEmpty();
        assertThat(artworkRepository.findById(artworkId).orElseThrow().isPortfolioIncluded()).isFalse();
    }

    // === 구성 교체 경로의 쓰기 순서 (§8.9 결함 E) ===

    // 제목 변경은 구성 교체 뒤로 옮겨졌다(portfolio_items → portfolios 쓰기 순서 유지) — 두 변경이 여전히
    // 한 번의 호출로 함께 반영되고 "업데이트순" 기준도 갱신되는지 확인한다.
    @Test
    void 수정하기는_제목과_구성을_한_번에_반영한다() {
        String memberId = registerProMember();
        String keptArtworkId = uploadArtwork(memberId);
        String addedArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "원래 제목", ReflectionType.LIVE, List.of(keptArtworkId));
        Instant lastEditedAt = portfolioRepository.findById(created.id()).orElseThrow().getLastEditedAt();

        PortfolioInfo updated = portfolioService.updatePortfolio(
                memberId, created.id(), "바꾼 제목", List.of(keptArtworkId, addedArtworkId));

        assertThat(updated.title()).isEqualTo("바꾼 제목");
        assertThat(updated.artworks()).extracting(PortfolioArtworkCardInfo::artworkId)
                .containsExactly(keptArtworkId, addedArtworkId);
        Portfolio stored = portfolioRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("바꾼 제목");
        assertThat(stored.getItemCount()).isEqualTo(2);
        assertThat(stored.getLastEditedAt()).isAfter(lastEditedAt);
    }

    // 제목 검증이 구성 교체보다 뒤로 밀렸어도 실패는 여전히 전부 롤백된다 — 반쪽 상태가 남으면 안 된다.
    @Test
    void 작가_페이지_제목_변경_실패는_구성_교체까지_롤백한다() {
        String memberId = registerMember();
        String artworkId = uploadArtwork(memberId);
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();

        assertThatThrownBy(() -> portfolioService.updatePortfolio(
                memberId, artistPageId, "작가 페이지 제목", List.of(artworkId)))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("ARTIST_PAGE_TITLE_IMMUTABLE");

        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId)).isEmpty();
        assertThat(portfolioRepository.findById(artistPageId).orElseThrow().getItemCount()).isZero();
    }

    // 노출 위치 재선언은 편입 반영을 먼저 하고 공개 상태를 나중에 바꾼다(artworks보다 portfolio_items를 먼저
    // 쓰기 위함) — 편입 반영이 실패하면 공개 상태도 그대로여야 한다.
    @Test
    void 노출_위치_재선언이_실패하면_공개_상태도_바뀌지_않는다() {
        String memberId = registerProMember();
        String artistPageId = portfolioService.getSelectablePortfolios(memberId).getFirst().id();
        String artworkId = uploadReadyWithSelection(memberId, true, List.of(artistPageId));

        assertThatThrownBy(() -> artworkService.updatePublication(
                memberId, artworkId, false, List.of(UUID.randomUUID().toString())))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_NOT_FOUND");

        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        assertThat(artwork.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(artwork.isPortfolioIncluded()).isTrue();
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(artistPageId))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(artworkId);
    }

    // 구성 교체는 결과가 그대로여도 버전을 올려야 한다 — 올리지 않으면 @Version 검사가 통째로 스킵되고,
    // 그 상태의 벌크 DELETE가 동시에 커밋된 다른 트랜잭션의 구성을 지운다(§8.9 결함 B).
    @Test
    void 구성_교체는_결과가_같아도_포트폴리오_버전을_올린다() {
        String memberId = registerProMember();
        String artworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(artworkId));
        long before = versionOf(created.id());

        // 이미 담긴 작품을 다시 추가 — 병합 결과도 개수도 그대로다.
        portfolioService.addArtworks(memberId, created.id(), List.of(artworkId));

        assertThat(versionOf(created.id())).isGreaterThan(before);
    }

    // 오래된 버전을 쥔 요청은 벌크 DELETE로 남의 작품을 지우지 못하고 낙관적 락에 걸려야 한다.
    @Test
    void 저장된_버전이_먼저_바뀌면_뒤늦은_구성_교체는_거부된다() {
        String memberId = registerProMember();
        String baseArtworkId = uploadArtwork(memberId);
        String lateArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(baseArtworkId));

        assertThatThrownBy(() -> inSingleTransaction(() -> {
            holdStaleReference(created.id());
            bumpStoredVersion(created.id());
            portfolioService.addArtworks(memberId, created.id(), List.of(lateArtworkId));
        }))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_CONCURRENTLY_MODIFIED");

        // 뒤늦은 교체는 통째로 롤백된다 — 먼저 반영된 쪽의 구성이 그대로 남는다.
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(baseArtworkId);
    }

    // 업로드 트랜잭션 안에서 포트폴리오 낙관적 락이 충돌하면 업로드까지 통째로 롤백된다 —
    // 편입 반영은 MANDATORY(같은 트랜잭션)라 반쪽 커밋이 남지 않는다(§8.9 남은 제약).
    @Test
    void 업로드_도중_포트폴리오_낙관적_락이_충돌하면_업로드_전체가_롤백된다() {
        String memberId = registerProMember();
        String baseArtworkId = uploadArtwork(memberId);
        PortfolioInfo created = portfolioService.createShared(
                memberId, "공유 포트폴리오", ReflectionType.LIVE, List.of(baseArtworkId));
        long artworkCountBefore = artworkCountOf(memberId);

        assertThatThrownBy(() -> inSingleTransaction(() -> {
            holdStaleReference(created.id());
            bumpStoredVersion(created.id());
            uploadWithSelection(memberId, true, List.of(created.id()));
        }))
                .isInstanceOf(PortfolioException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("PORTFOLIO_CONCURRENTLY_MODIFIED");

        // 충돌이 편입 리스너에서 삼켜지지 않고 업로드까지 전파돼 작품 행도 남지 않는다.
        assertThat(artworkCountOf(memberId)).isEqualTo(artworkCountBefore);
        assertThat(portfolioItemRepository.findByPortfolioIdOrderByOrdinal(created.id()))
                .extracting(PortfolioItem::getArtworkId)
                .containsExactly(baseArtworkId);
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

    /** 업로드-R09 조합(피드 공개 여부 × 담을 포트폴리오)을 그대로 넘기는 업로드. */
    private String uploadWithSelection(String memberId, boolean publishToFeed, List<String> portfolioIds) {
        return uploadWithSelection(memberId, publishToFeed, portfolioIds, "raw/" + UUID.randomUUID() + ".png");
    }

    /** 노출 위치 재선언(`updatePublication`)은 READY 상태를 요구하므로 이미지 처리까지 태운다. */
    private String uploadReadyWithSelection(String memberId, boolean publishToFeed, List<String> portfolioIds) {
        String imageKey = "raw/" + UUID.randomUUID() + ".png";
        String artworkId = uploadWithSelection(memberId, publishToFeed, portfolioIds, imageKey);
        mediaCallbackService.process(MediaOwnerType.ARTWORK, artworkId, imageKey,
                "thumb", null, "avif", MediaProcessingStatus.DONE);
        awaitReady(memberId, artworkId);
        return artworkId;
    }

    private String uploadWithSelection(String memberId, boolean publishToFeed, List<String> portfolioIds,
                                       String imageKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of("판타지"), List.of("태그"),
                AgeRating.ALL, publishToFeed, portfolioIds, List.of("clip studio"),
                new WorkDuration(1, 1, 1, 1), 1, List.of(), List.of())).id();
    }

    private String uploadArtwork(String memberId, String imageKey) {
        return artworkService.uploadArtwork(memberId, new UploadArtworkCommand(
                List.of(imageKey), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(ArtworkRole.LINEART), List.of("판타지"), List.of("태그"),
                AgeRating.ALL, true, List.of(), List.of("clip studio"),
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

    /**
     * 운영 차단 SQL을 재현한다 — 관리자 API가 없어 실제 운영도 같은 UPDATE로 수행한다
     * (docs/operations/moderation-block.md §2).
     */
    private void blockArtwork(String artworkId) {
        jdbcTemplate.update("UPDATE artworks SET blocked_at = UTC_TIMESTAMP(6) WHERE id = ?", artworkId);
    }

    /** 원본 차단과 짝을 이루는 스냅샷 차단 SQL — 원본만 막으면 배포된 고정형 링크로 스냅샷이 계속 노출된다. */
    private void blockSnapshotsOf(String artworkId) {
        jdbcTemplate.update(
                "UPDATE portfolio_item_snapshots SET blocked_at = UTC_TIMESTAMP(6) WHERE source_artwork_id = ?",
                artworkId);
    }

    /**
     * 라이트에서 넘어온 레거시 링크 공개 작품을 재현한다 — API 쓰기 경로는 400으로 막혀 있어(마이페이지_작가-R04)
     * 저장 상태를 직접 바꾼다.
     */
    @SuppressWarnings("deprecation")
    private void markLinkOnly(String artworkId) {
        Artwork artwork = artworkRepository.findById(artworkId).orElseThrow();
        artwork.changeVisibility(Visibility.LINK_ONLY);
        artworkRepository.save(artwork);
    }

    /** "업데이트순" 결과에서 공유 포트폴리오 ID만 순서대로 뽑는다 — 작가 페이지는 비교 대상이 아니다. */
    private List<String> sharedIdsByUpdated(String memberId) {
        return portfolioService
                .getMyPortfolios(memberId, PortfolioKind.SHARED, null, PortfolioSort.UPDATED, null, 20)
                .items().stream()
                .map(PortfolioSummaryInfo::id)
                .toList();
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

    /** 원본 변경 이벤트 구독도 @ApplicationModuleListener(비동기)라 개수 재계산 반영까지 폴링한다(§1.2). */
    private void awaitItemCount(String portfolioId, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (portfolioRepository.findById(portfolioId).orElseThrow().getItemCount() == expected) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("구성 개수 재계산 대기 시간 초과: portfolioId=" + portfolioId + " expected=" + expected);
    }

    /** 영구 삭제 이벤트 구독도 비동기라 구성 행 정리 반영까지 폴링한다(§1.2). */
    private void awaitItemArtworkIds(String portfolioId, List<String> expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            List<String> actual = portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolioId).stream()
                    .map(PortfolioItem::getArtworkId)
                    .toList();
            if (actual.equals(expected)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("구성 행 정리 대기 시간 초과: portfolioId=" + portfolioId + " expected=" + expected);
    }

    /**
     * 정렬 기준 시각을 마이크로초까지 지정한다 — 같은 밀리초·같은 마이크로초 상황은 실행 시각에 기대면
     * 재현되지 않으므로 저장 상태를 직접 맞춘다. 두 정렬 기준(createdAt/lastEditedAt)을 함께 맞춰
     * 정렬 옵션 3종을 같은 데이터로 검증한다.
     */
    private void setSortTimes(String portfolioId, String timestamp) {
        jdbcTemplate.update("UPDATE portfolios SET created_at = ?, last_edited_at = ? WHERE id = ?",
                timestamp, timestamp, portfolioId);
    }

    /** 커서를 끝까지 따라가며 공유 포트폴리오 ID를 모은다 — 페이지가 멈추면(커서 무한 반복) 실패시킨다. */
    private List<String> pageThroughShared(String memberId, PortfolioSort sort) {
        List<String> collected = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            CursorPage<PortfolioSummaryInfo> result =
                    portfolioService.getMyPortfolios(memberId, PortfolioKind.SHARED, null, sort, cursor, 1);
            result.items().forEach(item -> collected.add(item.id()));
            if (!result.hasNext()) {
                return collected;
            }
            cursor = result.nextCursor();
        }
        throw new AssertionError("커서 페이지네이션이 끝나지 않았다: sort=" + sort);
    }

    /** 낙관적 락 버전은 엔티티가 노출하지 않으므로 저장 상태를 직접 읽는다. */
    private long versionOf(String portfolioId) {
        Long version = jdbcTemplate.queryForObject("SELECT version FROM portfolios WHERE id = ?",
                Long.class, portfolioId);
        return version != null ? version : 0L;
    }

    /** 업로드가 통째로 롤백됐는지는 작품 행이 남았는지로 확인한다 — 실패 경로라 반환값을 받을 수 없다. */
    private long artworkCountOf(String memberId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artworks WHERE author_id = ?",
                Long.class, memberId);
        return count != null ? count : 0L;
    }

    /**
     * 낙관적 락 충돌 재현용 — 안에서 도는 서비스 호출을 한 트랜잭션(한 영속성 컨텍스트)에 묶는다.
     * 스레드나 중첩 트랜잭션은 쓰지 않는다(실제 잠금 대기로 테스트가 멈춘다).
     */
    private void inSingleTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    /** 포트폴리오를 미리 읽어 영속성 컨텍스트가 그 시점의 버전을 쥐게 한다 — 이후 서비스 호출이 이 참조를 쓴다. */
    private void holdStaleReference(String portfolioId) {
        portfolioRepository.findById(portfolioId).orElseThrow();
    }

    /**
     * 다른 요청이 포트폴리오를 먼저 바꾼 상황을 저장된 버전만 올려 흉내낸다 — 두 번째 트랜잭션이나
     * 스레드를 띄우지 않으므로 잠금 대기가 없다. 오래된 버전을 쥔 참조로 구성을 교체하면
     * 표준 낙관적 락 검사(UPDATE ... WHERE version = 쥐고 있던 값)가 0행에 걸린다.
     */
    private void bumpStoredVersion(String portfolioId) {
        jdbcTemplate.update("UPDATE portfolios SET version = version + 1 WHERE id = ?", portfolioId);
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
