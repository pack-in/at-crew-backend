package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.billing.PlanService;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.PortfolioArtworkCardInfo;
import com.atcrew.portfolio.PortfolioCoverThumbnailInfo;
import com.atcrew.portfolio.PortfolioDuplicationSourceInfo;
import com.atcrew.portfolio.PortfolioInfo;
import com.atcrew.portfolio.PortfolioKind;
import com.atcrew.portfolio.PortfolioSelectableInfo;
import com.atcrew.portfolio.PortfolioSharedInfo;
import com.atcrew.portfolio.PortfolioSort;
import com.atcrew.portfolio.PortfolioSummaryInfo;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItem;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import com.atcrew.portfolio.internal.exception.PortfolioErrorCode;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioItemSnapshotRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 포트폴리오 CRUD (docs/design/portfolio-module-design.md §4, §5).
 *
 * <p>portfolio는 다른 모듈이 동기 호출할 필요가 없는 리프 모듈이라 공개 인터페이스를 두지 않는다(§3).
 * 같은 모듈의 컨트롤러가 이 클래스를 직접 주입받는다(media의 {@code MediaCallbackService}와 동일한 형태).
 *
 * <p>작품 편입 여부(`artworks.portfolio_included`)는 이벤트가 아니라 같은 트랜잭션 안의 동기 호출로
 * 갱신한다 — 순서 역전이 없고 절대값 재계산이라 멱등하다(§1.2, §5.4).
 */
@Service
public class PortfolioServiceImpl {

    // 카드 커버는 2x2 배치라 최대 4장이다(마이페이지_작가-R39).
    private static final int COVER_THUMBNAIL_LIMIT = 4;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final PortfolioItemSnapshotRepository portfolioItemSnapshotRepository;
    private final ArtworkService artworkService;
    private final MemberService memberService;
    private final PlanService planService;
    private final ShareSlugGenerator shareSlugGenerator;
    private final PortfolioBlocker portfolioBlocker;
    // Spring Boot 4가 자동 구성한 Jackson 3 매퍼 — HTTP 응답 직렬화와 동일한 설정으로 스냅샷 JSON을 만든다.
    private final JsonMapper jsonMapper;

    PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                         PortfolioItemRepository portfolioItemRepository,
                         PortfolioItemSnapshotRepository portfolioItemSnapshotRepository,
                         ArtworkService artworkService,
                         MemberService memberService,
                         PlanService planService,
                         ShareSlugGenerator shareSlugGenerator,
                         PortfolioBlocker portfolioBlocker,
                         JsonMapper jsonMapper) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioItemRepository = portfolioItemRepository;
        this.portfolioItemSnapshotRepository = portfolioItemSnapshotRepository;
        this.artworkService = artworkService;
        this.memberService = memberService;
        this.planService = planService;
        this.shareSlugGenerator = shareSlugGenerator;
        this.portfolioBlocker = portfolioBlocker;
        this.jsonMapper = jsonMapper;
    }

    // === 조회 ===

    /**
     * 내 포트폴리오 목록. 작가 페이지가 아직 없으면 이 시점에 만든다(§2.5) — 따라서 읽기 전용 트랜잭션이 아니다.
     */
    @Transactional
    public CursorPage<PortfolioSummaryInfo> getMyPortfolios(String memberId, PortfolioKind kind,
                                                            ReflectionType reflectionType,
                                                            PortfolioSort sort, String cursor, int size) {
        getOrCreateArtistPage(memberId);
        PortfolioSort appliedSort = sort != null ? sort : PortfolioSort.LATEST;
        int limit = size + 1;
        List<Portfolio> portfolios = portfolioRepository
                .findAll(buildMySpecification(memberId, kind, reflectionType, appliedSort, cursor),
                        PageRequest.of(0, limit, sortOf(appliedSort)))
                .getContent();
        if (portfolios.isEmpty()) {
            return CursorPage.empty();
        }
        boolean hasNext = portfolios.size() > size;
        List<Portfolio> page = hasNext ? portfolios.subList(0, size) : portfolios;
        String nextCursor = hasNext
                ? String.valueOf(cursorValueOf(page.get(page.size() - 1), appliedSort).toEpochMilli())
                : null;
        return CursorPage.of(
                page.stream().map(p -> PortfolioMapper.toSummaryInfo(p, loadCoverThumbnails(p))).toList(),
                nextCursor);
    }

    /**
     * 작품 업로드·수정 화면의 포트폴리오 선택 목록 — 작가 페이지 + 최신 반영형만, 고정형은 제외한다(§4).
     * 플랜별 선택 가능 여부는 프론트가 판단하므로 게이팅 없이 본인 소유 전부를 내려준다.
     */
    @Transactional
    public List<PortfolioSelectableInfo> getSelectablePortfolios(String memberId) {
        getOrCreateArtistPage(memberId);
        return portfolioRepository
                .findByOwnerMemberIdAndReflectionTypeOrderByCreatedAtAsc(memberId, ReflectionType.LIVE)
                .stream()
                // 작가 페이지를 항상 맨 앞에 둔다 — lazy 생성이라 생성 시각이 가장 이르다는 보장이 없다.
                .sorted(Comparator.comparingInt(p -> p.getKind() == PortfolioKind.ARTIST_PAGE ? 0 : 1))
                .map(PortfolioMapper::toSelectableInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioInfo getPortfolio(String memberId, String portfolioId) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        return PortfolioMapper.toInfo(portfolio, loadCards(portfolio));
    }

    /**
     * 복제 원본 조회 (§5.3). 여기서 복제본을 만들지는 않는다 — 프론트가 이 응답으로 생성 API를 다시 호출한다.
     * 원본 유형은 복제본에 상속되지 않고 매번 새로 선택하기 때문이다.
     *
     * <p>원본 구성이 비어 있어도 정상 응답한다 — 자동 선택 0개여도 복제 진행이 허용된다(마이페이지_작가-R41).
     */
    @Transactional(readOnly = true)
    public PortfolioDuplicationSourceInfo getDuplicationSource(String memberId, String portfolioId) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        List<String> candidateArtworkIds = duplicationCandidateIds(portfolio);
        List<String> selectedArtworkIds = candidateArtworkIds.stream()
                .filter(this::duplicable)
                .toList();
        return new PortfolioDuplicationSourceInfo(duplicationDefaultTitle(memberId, portfolio),
                selectedArtworkIds, candidateArtworkIds.size() - selectedArtworkIds.size());
    }

    // === 공유 링크 공개 열람 ===

    /**
     * 공유 포트폴리오 헤더 조회 (§4). 인증 없이 접근하며 소유자 검증도 하지 않는다 —
     * 링크를 아는 사람은 항상 열람 가능한 설계라 슬러그의 추측 불가능성이 유일한 보호막이다(§2.6).
     */
    @Transactional(readOnly = true)
    public PortfolioSharedInfo getSharedPortfolio(String identifier) {
        Portfolio portfolio = resolveSharedPortfolio(identifier);
        assertNotBlocked(portfolio);
        return new PortfolioSharedInfo(
                portfolio.getId(),
                portfolio.getKind(),
                portfolio.getReflectionType(),
                portfolio.getTitle(),
                resolveOwnerName(portfolio),
                portfolio.getItemCount(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }

    /**
     * 공유 포트폴리오의 작품 목록 조회 (§4). 순서는 ordinal 오름차순(업로드순) 고정이다.
     *
     * <p>최신 반영형·작가 페이지는 원본을 조회 시점에 읽으므로 원본이 사라졌거나 휴지통에 있는 작품은
     * 조용히 빠진다 — 링크 자체는 여전히 유효하다. 고정형은 원본을 보지 않고 스냅샷 행만 읽는다(§5.1).
     */
    @Transactional(readOnly = true)
    public CursorPage<PortfolioArtworkCardInfo> getSharedPortfolioArtworks(String identifier, String cursor, int size) {
        Portfolio portfolio = resolveSharedPortfolio(identifier);
        assertNotBlocked(portfolio);
        // 다음 페이지 존재 여부 판정을 위해 한 건 더 읽는다.
        Pageable pageable = PageRequest.of(0, size + 1);

        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            List<PortfolioItemSnapshot> rows = cursor == null
                    ? portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId(), pageable)
                    : portfolioItemSnapshotRepository.findByPortfolioIdAndOrdinalGreaterThanOrderByOrdinal(
                            portfolio.getId(), parseOrdinalCursor(cursor), pageable);
            boolean hasNext = rows.size() > size;
            List<PortfolioItemSnapshot> page = hasNext ? rows.subList(0, size) : rows;
            return CursorPage.of(page.stream().map(PortfolioMapper::toCardInfo).toList(),
                    hasNext ? String.valueOf(page.getLast().getOrdinal()) : null);
        }

        List<PortfolioItem> rows = cursor == null
                ? portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId(), pageable)
                : portfolioItemRepository.findByPortfolioIdAndOrdinalGreaterThanOrderByOrdinal(
                        portfolio.getId(), parseOrdinalCursor(cursor), pageable);
        boolean hasNext = rows.size() > size;
        List<PortfolioItem> page = hasNext ? rows.subList(0, size) : rows;
        List<PortfolioArtworkCardInfo> cards = page.stream()
                .map(item -> artworkService.getArtworkForIndexing(item.getArtworkId()))
                .flatMap(Optional::stream)
                .filter(artwork -> artwork.status() != ArtworkStatus.DELETED)
                .map(PortfolioMapper::toCardInfo)
                .toList();
        return CursorPage.of(cards, hasNext ? String.valueOf(page.getLast().getOrdinal()) : null);
    }

    /**
     * 공유 식별자 해석 — 슬러그 우선, 없으면 작가 페이지 handle로 해석한다(§2.6).
     * 22자 handle이 실제로 존재할 수 있어 두 식별자가 한 네임스페이스를 공유하므로 이 순서가 곧 정책이다.
     */
    private Portfolio resolveSharedPortfolio(String identifier) {
        return portfolioRepository.findByShareSlug(identifier)
                .or(() -> resolveArtistPageByHandle(identifier))
                .orElseThrow(() -> new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND,
                        "identifier=" + identifier));
    }

    // 탈퇴 회원은 handle이 클리어되므로 이 조회 자체가 실패한다 — 별도 활성 필터가 필요 없다.
    // 작가 페이지가 아직 lazy 생성되지 않았어도 여기서 만들지 않는다 — 열람 요청이 쓰기를 유발하면 안 된다.
    // 인프라 장애를 404로 둔갑시키지 않도록 회원 부재를 뜻하는 DomainException만 잡는다.
    private Optional<Portfolio> resolveArtistPageByHandle(String handle) {
        String ownerMemberId;
        try {
            ownerMemberId = memberService.findByHandle(handle).id();
        } catch (DomainException e) {
            return Optional.empty();
        }
        return portfolioRepository.findByOwnerMemberIdAndKind(ownerMemberId, PortfolioKind.ARTIST_PAGE);
    }

    /**
     * 열람 차단 판정 (§5.2) — blocked_at 플래그와 소유자 활성 여부를 이중으로 확인한다.
     * {@code MemberDeactivatedEvent}가 유실돼도 탈퇴 회원의 공유 링크가 살아 있으면 안 되기 때문이다.
     *
     * <p>회원 부재·탈퇴를 뜻하는 {@link DomainException}만 잡는다 — 인프라 장애까지 잡으면 일시적 오류로
     * 멀쩡한 포트폴리오를 영구 차단하게 된다.
     */
    private void assertNotBlocked(Portfolio portfolio) {
        if (portfolio.getBlockedAt() != null) {
            throw new PortfolioException(PortfolioErrorCode.PORTFOLIO_BLOCKED, "portfolioId=" + portfolio.getId());
        }
        try {
            memberService.findById(portfolio.getOwnerMemberId());
        } catch (DomainException e) {
            // 탈퇴·부재 회원 — 이벤트가 아직 반영되지 않은 상태이므로 이 자리에서 차단을 확정한다.
            portfolioBlocker.blockOwnedPortfolios(portfolio.getOwnerMemberId());
            throw new PortfolioException(PortfolioErrorCode.PORTFOLIO_BLOCKED, "portfolioId=" + portfolio.getId());
        }
    }

    // 헤더 "사용자 이름 / 포트폴리오 이름"용. 고정형은 생성 시점에 얼린 이름을 쓴다(§5.1, 마이페이지_작가-R44).
    private String resolveOwnerName(Portfolio portfolio) {
        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            return portfolio.getSnapshotOwnerName();
        }
        return memberService.findById(portfolio.getOwnerMemberId()).name();
    }

    // 공유 작품 목록 커서는 마지막 항목의 ordinal이다 — 정렬 기준이 ordinal 하나뿐이라 복합 커서가 필요 없다.
    private int parseOrdinalCursor(String cursor) {
        try {
            return Integer.parseInt(cursor);
        } catch (NumberFormatException e) {
            throw new PortfolioException(PortfolioErrorCode.INVALID_CURSOR, cursor);
        }
    }

    // === 생성 ===

    /**
     * 공유 포트폴리오 생성 (§5.1). 반영 유형은 생성 시 결정되고 이후 전환할 수 없다.
     */
    @Transactional
    public PortfolioInfo createShared(String memberId, String title, ReflectionType reflectionType,
                                      List<String> artworkIds) {
        planService.assertPro(memberId);
        String validatedTitle = validateTitle(title);
        List<ArtworkInfo> artworks = orderByUploadedAt(resolveOwnedArtworks(memberId, artworkIds));

        if (reflectionType == ReflectionType.SNAPSHOT) {
            return createSnapshot(memberId, validatedTitle, artworks);
        }
        Portfolio portfolio = portfolioRepository.saveAndFlush(Portfolio.createShared(
                memberId, ReflectionType.LIVE, validatedTitle, shareSlugGenerator.generate()));
        replaceItems(portfolio, artworks.stream().map(ArtworkInfo::id).toList());

        return PortfolioMapper.toInfo(portfolio, artworks.stream().map(PortfolioMapper::toCardInfo).toList());
    }

    /**
     * 고정형 생성 (§5.1) — 생성 시점의 작품 표시 정보와 작성자 프로필을 함께 얼린다.
     *
     * <p>{@code portfolio_items}가 아닌 {@code portfolio_item_snapshots}에 쓰고
     * {@code updatePortfolioInclusion}도 호출하지 않는다 — 고정형은 라이브 멤버십(완전 비공개 판정)에
     * 잡히지 않는다(§1.2, §5.4).
     *
     * <p>이미지 R2 키는 복사하지 않고 원본 키를 그대로 담는다 — 알려진 제약(§5.6, {@link PortfolioItemSnapshot} 참조).
     */
    private PortfolioInfo createSnapshot(String memberId, String title, List<ArtworkInfo> artworks) {
        MemberInfo owner = memberService.findById(memberId);
        Portfolio portfolio = Portfolio.createShared(
                memberId, ReflectionType.SNAPSHOT, title, shareSlugGenerator.generate());
        portfolio.markAsSnapshot(owner.name(),
                jsonMapper.writeValueAsString(new OwnerProfileSnapshot(owner.name(), owner.handle())));
        portfolioRepository.saveAndFlush(portfolio);

        List<PortfolioItemSnapshot> snapshots = new ArrayList<>();
        for (int ordinal = 0; ordinal < artworks.size(); ordinal++) {
            snapshots.add(toSnapshot(portfolio.getId(), ordinal, artworks.get(ordinal)));
        }
        portfolioItemSnapshotRepository.saveAll(snapshots);
        portfolio.updateItemCount(snapshots.size());

        return PortfolioMapper.toInfo(portfolio, snapshots.stream().map(PortfolioMapper::toCardInfo).toList());
    }

    // 카드용 컬럼과 상세 본문 JSON을 함께 채운다(§2.3 하이브리드 저장).
    // 썸네일 판정은 라이브 카드와 동일해야 하므로 PortfolioMapper.toCardInfo 결과를 그대로 쓴다.
    private PortfolioItemSnapshot toSnapshot(String portfolioId, int ordinal, ArtworkInfo artwork) {
        PortfolioArtworkCardInfo card = PortfolioMapper.toCardInfo(artwork);
        ArtworkSnapshotPayload payload = new ArtworkSnapshotPayload(
                artwork.images(),
                artwork.materials(),
                artwork.tags(),
                artwork.tools(),
                artwork.roles(),
                artwork.genres(),
                artwork.videoLinks(),
                artwork.description(),
                artwork.representativeImageIndex());
        return PortfolioItemSnapshot.of(portfolioId, ordinal, artwork.id(), artwork.title(),
                card.thumbKey(), card.thumbAdultKey(), artwork.ageRating(), artwork.artworkField(),
                artwork.createdAt(), jsonMapper.writeValueAsString(payload));
    }

    // === 수정 ===

    /**
     * 제목·구성 수정 (§4). 고정형은 항상 409, 작가 페이지는 제목 변경이 불가능하고 공유는 프로 전용이다.
     */
    @Transactional
    public PortfolioInfo updatePortfolio(String memberId, String portfolioId, String title,
                                         List<String> artworkIds) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        assertEditable(portfolio, memberId);
        if (title != null) {
            // 작가 페이지면 엔티티가 ARTIST_PAGE_TITLE_IMMUTABLE을 던진다.
            portfolio.updateTitle(validateTitle(title));
        }
        if (artworkIds != null) {
            List<ArtworkInfo> artworks = orderByUploadedAt(resolveOwnedArtworks(memberId, artworkIds));
            replaceItems(portfolio, artworks.stream().map(ArtworkInfo::id).toList());
        }
        // 변경 감지로 반영된다 — 별도 save() 호출은 중복이다.
        return PortfolioMapper.toInfo(portfolio, loadCards(portfolio));
    }

    /** 작품 추가 (§4). 기존 구성에 이어 붙인 뒤 업로드순으로 다시 정렬한다. */
    @Transactional
    public void addArtworks(String memberId, String portfolioId, List<String> artworkIds) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        assertEditable(portfolio, memberId);

        Map<String, ArtworkInfo> merged = new LinkedHashMap<>();
        for (ArtworkInfo artwork : loadItemArtworks(portfolioId)) {
            merged.put(artwork.id(), artwork);
        }
        for (ArtworkInfo artwork : resolveOwnedArtworks(memberId, artworkIds)) {
            merged.put(artwork.id(), artwork);
        }
        replaceItems(portfolio, orderByUploadedAt(List.copyOf(merged.values()))
                .stream().map(ArtworkInfo::id).toList());
    }

    /** 작품 제거 (§4). 남은 구성의 상대 순서는 유지한 채 ordinal만 다시 매긴다. */
    @Transactional
    public void removeArtwork(String memberId, String portfolioId, String artworkId) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        assertEditable(portfolio, memberId);
        if (!portfolioItemRepository.existsByPortfolioIdAndArtworkId(portfolioId, artworkId)) {
            throw new PortfolioException(PortfolioErrorCode.ARTWORK_NOT_FOUND, "artworkId=" + artworkId);
        }
        List<String> remaining = itemArtworkIds(portfolioId).stream()
                .filter(id -> !id.equals(artworkId))
                .toList();
        replaceItems(portfolio, remaining);
    }

    // === 삭제 ===

    /**
     * 포트폴리오 삭제 (§4). 작가 페이지는 삭제할 수 없다. 공유는 스타터로 다운그레이드된 뒤에도 삭제만은
     * 허용한다(§5.5, 요금제-R01) — 여기에 assertPro를 걸지 않는 것이 의도된 동작이다.
     */
    @Transactional
    public void deletePortfolio(String memberId, String portfolioId) {
        Portfolio portfolio = findOwned(memberId, portfolioId);
        if (portfolio.getKind() == PortfolioKind.ARTIST_PAGE) {
            throw new PortfolioException(PortfolioErrorCode.ARTIST_PAGE_NOT_DELETABLE, "portfolioId=" + portfolioId);
        }
        List<String> affectedArtworkIds = itemArtworkIds(portfolioId);
        portfolioItemRepository.deleteByPortfolioId(portfolioId);
        portfolioItemSnapshotRepository.deleteByPortfolioId(portfolioId);
        portfolioRepository.delete(portfolio);
        syncPortfolioInclusion(affectedArtworkIds);
    }

    // === 내부 ===

    /**
     * 작가 페이지 lazy 생성 (§2.5). 가입 시 자동 생성하지 않으므로 기존 회원 backfill이 필요 없다.
     */
    private Portfolio getOrCreateArtistPage(String memberId) {
        Optional<Portfolio> existing =
                portfolioRepository.findByOwnerMemberIdAndKind(memberId, PortfolioKind.ARTIST_PAGE);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return portfolioRepository.saveAndFlush(Portfolio.createArtistPage(memberId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 만든 경우 — uk_pf_owner_artist_page가 최종 방어선이다.
            // 상대 트랜잭션이 커밋한 행이 이 트랜잭션 스냅샷에서 보이면 그대로 쓰고,
            // 아직 보이지 않으면 그대로 실패시켜 클라이언트 재시도에 맡긴다.
            return portfolioRepository.findByOwnerMemberIdAndKind(memberId, PortfolioKind.ARTIST_PAGE)
                    .orElseThrow(() -> e);
        }
    }

    private Portfolio findOwned(String memberId, String portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND,
                        "portfolioId=" + portfolioId));
        if (!portfolio.getOwnerMemberId().equals(memberId)) {
            throw new PortfolioException(PortfolioErrorCode.PORTFOLIO_ACCESS_DENIED,
                    "portfolioId=" + portfolioId + " memberId=" + memberId);
        }
        return portfolio;
    }

    // 고정형은 생성 시점 구성이 얼어붙으므로 어떤 수정도 받지 않고(§5.2), 공유는 프로 전용이다(§5.5).
    private void assertEditable(Portfolio portfolio, String memberId) {
        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            throw new PortfolioException(PortfolioErrorCode.SNAPSHOT_PORTFOLIO_IMMUTABLE,
                    "portfolioId=" + portfolio.getId());
        }
        if (portfolio.getKind() == PortfolioKind.SHARED) {
            planService.assertPro(memberId);
        }
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new PortfolioException(PortfolioErrorCode.INVALID_PORTFOLIO_TITLE);
        }
        return title.trim();
    }

    /**
     * 요청받은 작품이 전부 본인 소유이고 삭제되지 않았는지 검증한다(§5.1-2).
     * 없음·타인 소유·삭제됨을 응답으로 구분하지 않는다 — 타인 작품 존재 여부가 새지 않게 하기 위함이다.
     */
    private List<ArtworkInfo> resolveOwnedArtworks(String memberId, List<String> artworkIds) {
        if (artworkIds == null || artworkIds.isEmpty()) {
            return List.of();
        }
        List<ArtworkInfo> artworks = new ArrayList<>();
        for (String artworkId : new LinkedHashSet<>(artworkIds)) {
            ArtworkInfo artwork = artworkService.getArtworkForIndexing(artworkId)
                    .orElseThrow(() -> new PortfolioException(PortfolioErrorCode.ARTWORK_NOT_FOUND,
                            "artworkId=" + artworkId));
            if (!memberId.equals(artwork.authorId()) || artwork.status() == ArtworkStatus.DELETED) {
                throw new PortfolioException(PortfolioErrorCode.ARTWORK_NOT_FOUND,
                        "artworkId=" + artworkId + " memberId=" + memberId);
            }
            artworks.add(artwork);
        }
        return artworks;
    }

    /** 포트폴리오 내 순서는 업로드순(오래된순) 고정이 MVP 규칙이다(§2.2, 마이페이지_작가-R16). */
    private List<ArtworkInfo> orderByUploadedAt(List<ArtworkInfo> artworks) {
        return artworks.stream()
                .sorted(Comparator.comparing(ArtworkInfo::createdAt))
                .toList();
    }

    private List<String> itemArtworkIds(String portfolioId) {
        return portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolioId).stream()
                .map(PortfolioItem::getArtworkId)
                .toList();
    }

    // 이미 담긴 작품 조회 — 원본이 사라졌거나 휴지통으로 간 작품은 구성에서 조용히 빠진다.
    // artwork에 배치 조회 API가 없어 건당 조회한다(설계 §5.1의 getArtworksForSnapshot 도입 시 대체 대상).
    private List<ArtworkInfo> loadItemArtworks(String portfolioId) {
        return itemArtworkIds(portfolioId).stream()
                .map(artworkService::getArtworkForIndexing)
                .flatMap(Optional::stream)
                .filter(artwork -> artwork.status() != ArtworkStatus.DELETED)
                .toList();
    }

    private List<PortfolioArtworkCardInfo> loadCards(Portfolio portfolio) {
        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            // 고정형은 원본을 보지 않고 스냅샷 행만 읽는다 — 원본 수정·삭제·비공개 전환에 영향받지 않는다(§5.1).
            return portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId()).stream()
                    .map(PortfolioMapper::toCardInfo)
                    .toList();
        }
        return loadItemArtworks(portfolio.getId()).stream()
                .map(PortfolioMapper::toCardInfo)
                .toList();
    }

    /**
     * 카드 커버 썸네일 (마이페이지_작가-R39) — 업로드일이 가장 오래된 4개를 2x2로 배치한다.
     * 포트폴리오 내 순서가 곧 업로드순(ordinal)이라 앞의 4건만 읽으면 된다.
     *
     * <p>4개 미만이면 있는 만큼만, 0개면 빈 배열로 내려간다 — 빈 칸 처리는 프론트가 배열 길이로 판단한다.
     * 최신 반영형·작가 페이지는 원본이 사라졌거나 휴지통에 있으면 그 칸이 빠지고(공유 열람과 동일 규칙),
     * 고정형은 원본을 조회하지 않고 스냅샷 컬럼을 그대로 쓴다(§5.1).
     */
    private List<PortfolioCoverThumbnailInfo> loadCoverThumbnails(Portfolio portfolio) {
        Pageable pageable = PageRequest.of(0, COVER_THUMBNAIL_LIMIT);
        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            return portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId(), pageable).stream()
                    .map(PortfolioMapper::toCoverThumbnailInfo)
                    .toList();
        }
        // artwork에 배치 조회 API가 없어 건당 조회한다(loadItemArtworks와 동일한 특성).
        return portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId(), pageable).stream()
                .map(item -> artworkService.getArtworkForIndexing(item.getArtworkId()))
                .flatMap(Optional::stream)
                .filter(artwork -> artwork.status() != ArtworkStatus.DELETED)
                .map(PortfolioMapper::toCoverThumbnailInfo)
                .toList();
    }

    /** 복제 후보 추출 (§5.3) — 고정형은 스냅샷의 원본 작품 ID를, 나머지는 라이브 구성을 순서 그대로 쓴다. */
    private List<String> duplicationCandidateIds(Portfolio portfolio) {
        if (portfolio.getReflectionType() == ReflectionType.SNAPSHOT) {
            return portfolioItemSnapshotRepository.findByPortfolioIdOrderByOrdinal(portfolio.getId()).stream()
                    .map(PortfolioItemSnapshot::getSourceArtworkId)
                    .toList();
        }
        return itemArtworkIds(portfolio.getId());
    }

    /**
     * 자동 선택 제외 판정 (§5.3, 마이페이지_작가-R41) — 영구 삭제됐거나 휴지통에 있거나 비공개인 작품은 뺀다.
     * LINK_ONLY는 "비공개"가 아니므로 그대로 포함한다. 소유자 검증은 하지 않는다 — 이미 소유가 확인된
     * 포트폴리오의 구성이라 전부 본인 작품이다.
     *
     * <p>고정형은 스냅샷이 아니라 원본의 현재 상태로 판정한다 — 복제본은 원본을 다시 담기 때문이다.
     */
    private boolean duplicable(String artworkId) {
        return artworkService.getArtworkForIndexing(artworkId)
                .filter(artwork -> artwork.status() != ArtworkStatus.DELETED)
                .filter(artwork -> artwork.visibility() != Visibility.PRIVATE)
                .isPresent();
    }

    // 작가 페이지는 제목이 없고 사용자 이름을 헤더로 쓰므로 기본 제목도 사용자 이름 기준이다(§5.3).
    private String duplicationDefaultTitle(String memberId, Portfolio portfolio) {
        String source = portfolio.getKind() == PortfolioKind.ARTIST_PAGE
                ? memberService.findById(memberId).name()
                : portfolio.getTitle();
        return source + " 복사본";
    }

    /**
     * 구성 교체 — 기존 행을 벌크 DELETE로 먼저 확정한 뒤 새 행을 넣는다.
     * 같은 flush에서 INSERT가 DELETE보다 먼저 실행되면 uk_pi_order·uk_pi_pf_artwork와 충돌하기 때문이다
     * ({@code ArtworkServiceImpl.replaceImages}와 동일 계열의 함정).
     */
    private void replaceItems(Portfolio portfolio, List<String> orderedArtworkIds) {
        Set<String> affectedArtworkIds = new LinkedHashSet<>(itemArtworkIds(portfolio.getId()));
        affectedArtworkIds.addAll(orderedArtworkIds);

        portfolioItemRepository.deleteByPortfolioId(portfolio.getId());
        List<PortfolioItem> items = new ArrayList<>();
        for (int ordinal = 0; ordinal < orderedArtworkIds.size(); ordinal++) {
            items.add(PortfolioItem.of(portfolio.getId(), orderedArtworkIds.get(ordinal), ordinal));
        }
        portfolioItemRepository.saveAllAndFlush(items);

        portfolio.updateItemCount(items.size());
        syncPortfolioInclusion(affectedArtworkIds);
    }

    /**
     * 라이브 멤버십 절대값 재계산 후 artwork에 반영한다(§5.4). 고정형은 portfolio_items를 쓰지 않으므로
     * 이 카운트에 잡히지 않는다(§1.2).
     */
    private void syncPortfolioInclusion(Collection<String> artworkIds) {
        for (String artworkId : artworkIds) {
            if (artworkService.getArtworkForIndexing(artworkId).isEmpty()) {
                // 영구 삭제된 원본 — 갱신 대상이 없다.
                continue;
            }
            artworkService.updatePortfolioInclusion(artworkId,
                    portfolioItemRepository.countByArtworkId(artworkId) > 0);
        }
    }

    private Specification<Portfolio> buildMySpecification(String memberId, PortfolioKind kind,
                                                          ReflectionType reflectionType,
                                                          PortfolioSort sort, String cursor) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerMemberId"), memberId));
            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (reflectionType != null) {
                predicates.add(cb.equal(root.get("reflectionType"), reflectionType));
            }
            if (cursor != null) {
                Instant cursorValue = parseCursor(cursor);
                predicates.add(sort == PortfolioSort.OLDEST
                        ? cb.greaterThan(root.get(cursorField(sort)), cursorValue)
                        : cb.lessThan(root.get(cursorField(sort)), cursorValue));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort sortOf(PortfolioSort sort) {
        return switch (sort) {
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case UPDATED -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
    }

    private String cursorField(PortfolioSort sort) {
        return sort == PortfolioSort.UPDATED ? "updatedAt" : "createdAt";
    }

    private Instant cursorValueOf(Portfolio portfolio, PortfolioSort sort) {
        return sort == PortfolioSort.UPDATED ? portfolio.getUpdatedAt() : portfolio.getCreatedAt();
    }

    // 커서는 기준 컬럼의 epochMilli 단일값이다(artwork 목록과 동일 관례).
    private Instant parseCursor(String cursor) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new PortfolioException(PortfolioErrorCode.INVALID_CURSOR, cursor);
        }
    }
}
