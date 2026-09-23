package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkAccess;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSort;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.MaterialData;
import com.atcrew.artwork.PresignedUrlInfo;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPortfolioSelectionRequested;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.artwork.Material;
import com.atcrew.artwork.internal.domain.view.ArtworkHotScore;
import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.billing.BillingService;
import com.atcrew.common.response.CursorPage;
import com.atcrew.common.response.OffsetPage;
import com.atcrew.media.MediaConstraints;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaAssetSpec;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
class ArtworkServiceImpl implements ArtworkService {

    private static final Logger log = LoggerFactory.getLogger(ArtworkServiceImpl.class);
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    /** 스타터 플랜 보유 작품 상한(마이페이지_작가-R20). */
    private static final int STARTER_ARTWORK_LIMIT = 4;
    // 게시물 언어 칩 4종(로그인-R19) — 프로도 전체 선택이 상한이다
    private static final int MAX_LANGUAGE_COUNT = 4;
    /** 이번 주 가장 핫한 작품 노출 상한(홈-R03, Figma promotion Card). */
    private static final int HOT_ARTWORK_LIMIT = 6;
    /** 기간 조회수가 같을 때의 순서(홈-R14) — 북마크 수 → 등록일(최신) → ID. ID는 동률 순서를 고정하는 마지막 키다. */
    private static final Sort HOT_TIEBREAK_SORT = Sort.by(
            Sort.Order.desc("bookmarkCount"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    // FE가 crypto.randomUUID()로 발급하는 표준 36자 표기만 받는다. UUID.fromString은 "1-1-1-1-1"도 받아 같은
    // 값이 여러 표기로 dedup을 비껴갈 수 있다.
    private static final Pattern ANONYMOUS_ID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ArtworkRepository artworkRepository;
    private final MemberService memberService;
    private final MediaService mediaService;
    private final BillingService billingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ArtworkPurger artworkPurger;
    private final ArtworkViewRecorder artworkViewRecorder;

    ArtworkServiceImpl(ArtworkRepository artworkRepository,
                       MemberService memberService,
                       MediaService mediaService,
                       BillingService billingService,
                       ApplicationEventPublisher eventPublisher,
                       ArtworkPurger artworkPurger,
                       ArtworkViewRecorder artworkViewRecorder) {
        this.artworkRepository = artworkRepository;
        this.memberService = memberService;
        this.mediaService = mediaService;
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
        this.artworkPurger = artworkPurger;
        this.artworkViewRecorder = artworkViewRecorder;
    }

    @Override
    public List<PresignedUrlInfo> generatePresignedUrls(String memberId, int count, List<String> contentTypes, List<Long> fileSizes) {
        // 발급 자체는 media에 위임하지만(docs/design/media-module-design.md §9.1-3), 입력 검증은 여기 남긴다 —
        // media는 IllegalArgumentException을 던지므로 그대로 흘리면 기존 400 ARTWORK 에러코드가 500으로 바뀐다.
        if (count < 1 || count > 30) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT);
        }
        if (count != contentTypes.size()) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT, "count와 contentTypes 수가 일치해야 합니다");
        }
        for (String ct : contentTypes) {
            if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_CONTENT_TYPE, ct);
            }
        }
        // 크기는 클라이언트가 보낼 때만 검사한다 — 보내지 않는 구버전 클라이언트도 계속 발급받되,
        // 상한 초과분은 Worker가 변환 직전 실측으로 걸러 FAILED가 된다.
        if (fileSizes != null) {
            if (fileSizes.size() != count) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT, "count와 fileSizes 수가 일치해야 합니다");
            }
            for (Long size : fileSizes) {
                if (size != null && size > MediaConstraints.MAX_ORIGINAL_BYTES) {
                    throw new ArtworkException(ArtworkErrorCode.IMAGE_TOO_LARGE, size + "바이트");
                }
            }
        }
        // 발급 한도(#216) — 넘으면 429. 등록되지 않은 원본이 무한히 쌓이는 것을 막는다.
        if (!mediaService.tryReservePresign(memberId, count)) {
            throw new ArtworkException(ArtworkErrorCode.PRESIGN_RATE_LIMITED);
        }
        return mediaService.generatePresignedUrls(memberId, count, contentTypes, fileSizes).stream()
                .map(info -> new PresignedUrlInfo(info.key(), info.uploadUrl()))
                .toList();
    }

    @Override
    @Transactional
    public ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command) {
        assertArtworkQuota(memberId, 1);
        assertLanguagesAllowed(memberId, command.languages());
        // 새 작품이라 물려받을 key가 없다 — 모든 key가 본인에게 발급된 것이어야 한다(#190).
        assertKeysOwned(memberId,
                submittedKeys(command.imageKeys(), command.thumbnailKey(), command.materials()), Set.of());
        assertThumbnailSeparate(command.thumbnailKey(), command.imageKeys());
        List<Material> materials = toMaterials(command.materials());
        Artwork artwork = Artwork.create(
                memberId,
                command.title(),
                command.description(),
                command.imageKeys(),
                command.representativeImageIndex(),
                command.thumbnailKey(),
                command.imageLayoutType(),
                command.artworkField(),
                command.creativeType(),
                command.roles(),
                command.genres(),
                command.customTags(),
                command.tags(),
                command.ageRating(),
                command.languages(),
                visibilityOf(command.publishToFeed()),
                command.tools(),
                command.workDuration(),
                command.cutCount(),
                command.videoLinks(),
                materials
        );
        Artwork saved = artworkRepository.save(artwork);
        // 포트폴리오 편입은 portfolio가 이 트랜잭션 안에서 동기 처리한다 — 검증 실패 시 업로드까지 롤백된다.
        // 이미지 처리 트리거는 커밋 뒤에만 나가므로(#174) 이 순서가 롤백 가능성에 영향을 주지는 않는다.
        eventPublisher.publishEvent(new ArtworkPortfolioSelectionRequested(
                memberId, saved.getId(), command.portfolioIds()));
        MediaQualityTier qualityTier = qualityTierOf(memberId);
        mediaService.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, saved.getId(),
                command.imageKeys(), MediaVariantProfile.ORIGINAL, qualityTier);
        if (command.thumbnailKey() != null) {
            mediaService.registerAndTriggerProcessing(MediaOwnerType.ARTWORK_THUMBNAIL, saved.getId(),
                    List.of(command.thumbnailKey()), MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, qualityTier);
        }
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        log.info("작품 업로드 완료: artworkId={} memberId={}", saved.getId(), memberId);
        return toInfo(saved, author);
    }

    @Override
    @Transactional(readOnly = true)
    public ArtworkInfo getArtwork(String artworkId, String viewerMemberId) {
        Artwork artwork = findArtworkById(artworkId);
        switch (artwork.accessFor(viewerMemberId)) {
            case NOT_FOUND -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
            case DELETED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_DELETED, artworkId);
            case PRIVATE -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_PRIVATE, artworkId);
            case BLOCKED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_BLOCKED, artworkId);
            case ALLOWED -> { }
        }
        // 조회수는 여기서 올리지 않는다(홈-R14) — 이 GET은 FE SSR이 토큰 없이 부르고 편집 화면·포트폴리오도 부르므로
        // 열람자를 식별할 수 없다. 집계는 브라우저가 호출하는 recordView(POST /views)가 담당한다.
        MemberInfo author = memberService.findById(artwork.getAuthorId());
        return toInfo(artwork, author);
    }

    /**
     * 트랜잭션 격리 수준을 READ COMMITTED로 낮춘다. 기본값(REPEATABLE READ)에서는 없는 dedup 행을 조건부
     * UPDATE할 때 갭 락이 잡혀, 같은 작품을 처음 여는 두 요청이 서로의 갭 락 때문에 INSERT를 못 하고 교착된다
     * (열람자가 달라도 인덱스상 같은 갭이면 생긴다). READ COMMITTED에는 갭 락이 없어 PK 행 락만으로 직렬화된다.
     * 운영 DB는 바이너리 로그를 쓰지 않아(deploy/docker-compose.app.yml) STATEMENT 형식 제약도 해당하지 않는다.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordView(String artworkId, String viewerMemberId, String anonymousId) {
        ArtworkViewerType viewerType;
        String viewerKey;
        if (viewerMemberId != null) {
            // 회원 우선 — 헤더는 보지 않는다. FE는 로그인 후에도 익명 쿠키를 계속 보내므로 헤더 형식 오류로
            // 회원 열람까지 400이 되면 안 된다.
            // JWT 필터는 탈퇴 여부를 보지 않아 탈퇴 직후에도 액세스 토큰이 만료 전까지 통과한다. 여기서 막지 않으면
            // 탈퇴 비식별화(ArtworkViewMemberEventListener)가 끝난 뒤 회원 ID가 열람 기록에 다시 영구 저장된다.
            if (memberService.findAllByIds(Set.of(viewerMemberId)).isEmpty()) {
                return;
            }
            viewerType = ArtworkViewerType.MEMBER;
            viewerKey = viewerMemberId;
        } else if (anonymousId != null) {
            if (!ANONYMOUS_ID_PATTERN.matcher(anonymousId).matches()) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_ANONYMOUS_ID, "anonymousId=" + anonymousId);
            }
            // 대소문자만 다른 같은 UUID가 서로 다른 열람자로 집계되지 않게 한다.
            viewerType = ArtworkViewerType.ANONYMOUS;
            viewerKey = anonymousId.toLowerCase(Locale.ROOT);
        } else {
            return; // 식별할 수 없는 열람 — dedup 키가 없으면 새로고침만으로 조회수가 오른다
        }
        Optional<Artwork> artwork = artworkRepository.findById(artworkId);
        if (artwork.isEmpty()
                || artwork.get().getAuthorId().equals(viewerMemberId)
                || artwork.get().accessFor(viewerMemberId) != ArtworkAccess.ALLOWED) {
            return; // 없는 작품·본인 작품·열람 불가 작품은 집계하지 않는다(응답으로 구분하지 않는다)
        }
        artworkViewRecorder.record(artworkId, viewerType, viewerKey, Instant.now());
    }

    @Override
    public ArtworkStatus getArtworkStatus(String memberId, String artworkId) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.assertOwner(memberId);
        return artwork.getStatus();
    }

    @Override
    @Transactional
    public ArtworkInfo updateArtwork(String memberId, String artworkId, UpdateArtworkCommand command) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.assertOwner(memberId);
        if (artwork.getStatus() == ArtworkStatus.DELETED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
        }

        if (command.languages() != null) {
            assertLanguagesAllowed(memberId, command.languages());
        }
        assertKeysOwned(memberId,
                submittedKeys(command.imageKeys(), command.thumbnailKey(), command.materials()),
                storedKeysOf(artwork));
        String previousThumbnailKey = artwork.getThumbnailKey();
        assertThumbnailSeparate(command.thumbnailKey() != null ? command.thumbnailKey() : previousThumbnailKey,
                command.imageKeys() != null ? command.imageKeys()
                        : mediaService.getAssets(MediaOwnerType.ARTWORK, artwork.getId()).stream()
                                .map(MediaAssetInfo::originalKey).toList());

        if (command.materials() != null) {
            replaceMaterials(artwork, toMaterials(command.materials()));
        }

        artwork.updateDetails(
                command.title(),
                command.description(),
                command.imageLayoutType(),
                command.thumbnailKey(),
                command.artworkField(),
                command.creativeType(),
                command.roles(),
                command.genres(),
                command.customTags(),
                command.tags(),
                command.ageRating(),
                command.languages(),
                command.tools(),
                command.workDuration(),
                command.cutCount(),
                command.videoLinks()
        );

        // 이미지 목록은 media가 맞춘다 — 남는 이미지는 변환 결과를 그대로 두고, 빠진 것만 고아 큐로 간다(#193).
        // 프론트가 수정 요청마다 폼 전체를 보내 imageKeys가 항상 실려 오므로, 바뀐 게 없으면 여기서 아무 일도
        // 일어나지 않는다.
        List<MediaAssetInfo> images = command.imageKeys() != null
                ? mediaService.syncAssets(MediaOwnerType.ARTWORK, artwork.getId(),
                        command.imageKeys().stream().map(MediaAssetSpec::of).toList(),
                        MediaVariantProfile.ORIGINAL, qualityTierOf(memberId))
                : mediaService.getAssets(MediaOwnerType.ARTWORK, artwork.getId());
        MediaAssetInfo thumbnail = syncThumbnail(memberId, artwork.getId(), previousThumbnailKey,
                command.thumbnailKey());
        if (command.representativeImageIndex() != null) {
            artwork.repositionRepresentative(command.representativeImageIndex(), images.size());
        }
        artwork.applyImageStatuses(images.stream().map(MediaAssetInfo::status)
                .map(ArtworkMapper::toImageStatus).toList());

        Artwork saved = artworkRepository.save(artwork);
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        return ArtworkMapper.toInfo(saved, author, new ArtworkMedia(images, thumbnail));
    }

    /**
     * 사용자 지정 썸네일을 요청에 맞춘다 — 바뀐 경우에만 손댄다.
     *
     * <p>같은 key로 다시 동기화하지 않는 이유: 썸네일 변환 이전에 올라온 작품은 자산 행이 없어서, 동기화하면 새로
     * 등록돼 변환되고 Worker가 raw를 지운다. 고정형 스냅샷이 그 raw key를 카드 썸네일로 참조하고 있으면 깨진다.
     *
     * <p>이전 썸네일이 자산이었으면 {@code syncAssets}가 원본·변형본을 고아 큐로 보낸다. 자산이 없던 옛 raw는 여기서
     * 직접 고아 큐에 넣는다 — 예전에는 교체된 썸네일이 어디서도 정리되지 않았다. 스냅샷이 참조 중이면 정리 스케줄러의
     * 보존 판정이 유예한다.
     *
     * @return 요청 반영 뒤의 썸네일 자산. 없으면 null
     */
    private MediaAssetInfo syncThumbnail(String memberId, String artworkId, String previousKey, String requestedKey) {
        List<MediaAssetInfo> current = mediaService.getAssets(MediaOwnerType.ARTWORK_THUMBNAIL, artworkId);
        if (requestedKey == null || requestedKey.equals(previousKey)) {
            return current.isEmpty() ? null : current.get(0);
        }
        if (current.isEmpty() && previousKey != null) {
            mediaService.markOrphaned(List.of(previousKey));
        }
        List<MediaAssetInfo> synced = mediaService.syncAssets(MediaOwnerType.ARTWORK_THUMBNAIL, artworkId,
                List.of(MediaAssetSpec.of(requestedKey)), MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR,
                qualityTierOf(memberId));
        return synced.get(0);
    }

    /** 썸네일과 본문이 같은 업로드 key를 쓰면 거부한다({@link ArtworkErrorCode#THUMBNAIL_KEY_IN_IMAGES}). */
    private static void assertThumbnailSeparate(String thumbnailKey, List<String> imageKeys) {
        if (thumbnailKey != null && imageKeys != null && imageKeys.contains(thumbnailKey)) {
            throw new ArtworkException(ArtworkErrorCode.THUMBNAIL_KEY_IN_IMAGES, thumbnailKey);
        }
    }

    // 자재 교체 — replaceImages와 동일한 이유로 uk_am_order(artwork_id, ordinal) 충돌을 막기 위해 2단계로 처리한다.
    private void replaceMaterials(Artwork artwork, List<Material> newMaterials) {
        artwork.detachMaterials();
        artworkRepository.saveAndFlush(artwork);
        artwork.attachMaterials(newMaterials);
    }

    @Override
    @Transactional
    public void updatePublication(String memberId, String artworkId, boolean publishToFeed,
                                  List<String> portfolioIds) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.assertOwner(memberId);
        // 편입 반영이 먼저다 — 실패하면 공개 상태 변경까지 함께 롤백돼야 반쪽 상태가 남지 않는다.
        // 공개 상태 변경을 뒤로 미루는 것도 같은 트랜잭션 안의 쓰기 순서 때문이다 — artworks를 먼저 dirty로
        // 만들면 편입 반영의 벌크 DELETE(flushAutomatically)가 artworks를 portfolio_items보다 먼저 flush해,
        // portfolio_items → artworks 순으로 쓰는 포트폴리오 삭제 경로와 잠금 순서가 어긋난다
        // (docs/design/portfolio-module-design.md §8.9).
        eventPublisher.publishEvent(new ArtworkPortfolioSelectionRequested(memberId, artworkId, portfolioIds));
        artwork.changeVisibility(visibilityOf(publishToFeed));
        artworkRepository.save(artwork);
        eventPublisher.publishEvent(new ArtworkChangedEvent(artworkId));
    }

    // 공개 상태는 사용자가 고르는 값이 아니라 노출 위치 조합에서 계산되는 파생값이다(업로드-R09).
    // 라이브 포트폴리오 편입까지 반영한 최종 접근 판정은 Artwork.accessFor가 담당한다(마이페이지_작가-R04).
    // "링크 공개"라는 제3의 상태는 없으므로 LINK_ONLY를 새로 만들어내는 경로도 없다.
    private Visibility visibilityOf(boolean publishToFeed) {
        return publishToFeed ? Visibility.PUBLIC : Visibility.PRIVATE;
    }

    @Override
    @Transactional
    public void deleteArtwork(String memberId, String artworkId) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.assertOwner(memberId);
        artwork.moveToTrash();
        artworkRepository.save(artwork);
        eventPublisher.publishEvent(new ArtworkChangedEvent(artworkId));
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<ArtworkSummaryInfo> getCommunityArtworks(ArtworkField artworkField,
                                                               AgeRating ageRating,
                                                               List<Language> viewerLanguages,
                                                               ArtworkSort sort,
                                                               int page, int size,
                                                               String viewerMemberId,
                                                               boolean viewerAdultContentVisible) {
        ArtworkSort resolvedSort = sort != null ? sort : ArtworkSort.LATEST;
        Specification<Artwork> spec = buildCommunitySpecification(artworkField, ageRating, viewerLanguages,
                viewerMemberId, viewerAdultContentVisible);
        // 전체 개수는 이 조회가 이미 센 값을 쓴다 — 따로 count를 부르면 같은 COUNT가 두 번 실행된다.
        Page<Artwork> found = artworkRepository.findAll(spec, PageRequest.of(page - 1, size, sortOf(resolvedSort)));
        return new OffsetPage<>(toSummaryInfos(found.getContent()), found.getTotalElements());
    }

    /**
     * 후보 필터는 커뮤니티 피드와 같은 {@link #buildCommunitySpecification}을 그대로 쓴다(분야·연령 필터 없음).
     * 두 조회를 한 읽기 트랜잭션에 묶어 같은 스냅샷을 보게 한다 — 그 사이 매시간 배치가 점수를 다시 쓰면
     * 1단계와 2단계가 서로 다른 점수표를 보고 같은 작품을 두 번 뽑거나 빠뜨릴 수 있다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ArtworkSummaryInfo> getHotArtworks(List<Language> viewerLanguages, String viewerMemberId,
                                                   boolean viewerAdultContentVisible) {
        Specification<Artwork> candidates = buildCommunitySpecification(null, null, viewerLanguages,
                viewerMemberId, viewerAdultContentVisible);
        List<Artwork> picked = new ArrayList<>(artworkRepository
                .findAll(candidates.and(rankedByWindowViews()), PageRequest.of(0, HOT_ARTWORK_LIMIT))
                .getContent());
        if (picked.size() < HOT_ARTWORK_LIMIT) {
            // 점수 행이 있는 후보는 1단계에서 전부 뽑혔으므로 점수 행이 없는(기간 조회수 0) 후보만 보면 중복이 없다.
            picked.addAll(artworkRepository
                    .findAll(candidates.and(withoutWindowViews()),
                            PageRequest.of(0, HOT_ARTWORK_LIMIT - picked.size(), HOT_TIEBREAK_SORT))
                    .getContent());
        }
        return toSummaryInfos(picked);
    }

    /**
     * 기간 조회수가 있는 후보만 남기고 기간 조회수 순으로 정렬한다. 정렬 키가 다른 엔티티(점수표)에 있어
     * {@link Sort}로는 표현할 수 없으므로 상관 서브쿼리로 정렬한다 — 대상이 점수 행이 있는 작품으로 좁혀진
     * 뒤라 작품 수만큼 서브쿼리가 도는 비용은 작다.
     */
    private Specification<Artwork> rankedByWindowViews() {
        return (root, query, cb) -> {
            // 페이지 조회가 함께 부르는 count 쿼리에는 정렬을 붙이지 않는다.
            if (!Long.class.equals(query.getResultType())) {
                Subquery<Long> windowViews = query.subquery(Long.class);
                Root<ArtworkHotScore> score = windowViews.from(ArtworkHotScore.class);
                windowViews.select(score.get("windowViews"))
                        .where(cb.equal(score.get("artworkId"), root.get("id")));
                query.orderBy(cb.desc(windowViews), cb.desc(root.get("bookmarkCount")),
                        cb.desc(root.get("createdAt")), cb.asc(root.get("id")));
            }
            return cb.exists(hotScoreOf(root, query, cb));
        };
    }

    private Specification<Artwork> withoutWindowViews() {
        return (root, query, cb) -> cb.not(cb.exists(hotScoreOf(root, query, cb)));
    }

    private Subquery<String> hotScoreOf(Root<Artwork> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<String> subquery = query.subquery(String.class);
        Root<ArtworkHotScore> score = subquery.from(ArtworkHotScore.class);
        return subquery.select(score.get("artworkId"))
                .where(cb.equal(score.get("artworkId"), root.get("id")));
    }

    // Mongo Criteria 동적 쿼리 → JPA Specification (docs/design/mariadb-migration-design.md §3.6)
    private Specification<Artwork> buildCommunitySpecification(ArtworkField artworkField,
                                                                AgeRating ageRating,
                                                                List<Language> viewerLanguages,
                                                                String viewerMemberId,
                                                                boolean viewerAdultContentVisible) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ArtworkStatus.READY));
            predicates.add(cb.equal(root.get("visibility"), Visibility.PUBLIC));
            // 운영 차단된 작품은 외부 노출을 즉시 중단한다(마이페이지_작가-R39).
            predicates.add(cb.isNull(root.get("blockedAt")));
            if (artworkField != null) {
                predicates.add(cb.equal(root.get("artworkField"), artworkField));
            }
            if (ageRating != null) {
                predicates.add(cb.equal(root.get("ageRating"), ageRating));
            }
            if (viewerLanguages != null && !viewerLanguages.isEmpty()) {
                predicates.add(languageSegmentPredicate(root, query, cb, viewerLanguages));
            }
            if (!viewerAdultContentVisible) {
                predicates.add(adultContentPredicate(root, cb, viewerMemberId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 커뮤니티 피드 정렬(이슈 #78) — 어떤 기준이든 <b>(정렬 키, id)</b> 2단으로 정렬한다.
     *
     * <p>정렬 키 하나만으로 정렬하면 값이 같은 행들의 순서를 DB가 보장하지 않아, 페이지를 넘길 때마다
     * 같은 값 묶음의 순서가 달라져 커서 페이지네이션에서 레코드가 빠지거나 중복된다. 조회수·북마크 수는
     * 신규 작품이 전부 0이라 이 상황이 기본값이다. id는 UUIDv7이라 고유하면서 생성 시각순이라
     * tiebreaker로 적합하다.
     */
    private Sort sortOf(ArtworkSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt", "id");
            case VIEW_COUNT -> Sort.by(Sort.Direction.DESC, "viewCount", "id");
            case BOOKMARK_COUNT -> Sort.by(Sort.Direction.DESC, "bookmarkCount", "id");
        };
    }

    @Override
    public CursorPage<ArtworkSummaryInfo> getMyArtworks(String memberId, String cursor, int size) {
        int limit = size + 1;
        List<Artwork> artworks = cursor != null
                ? artworkRepository.findByAuthorIdAndStatusNotAndCreatedAtBeforeOrderByCreatedAtDesc(
                        memberId, ArtworkStatus.DELETED, parseCursor(cursor), PageRequest.of(0, limit))
                : artworkRepository.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(
                        memberId, ArtworkStatus.DELETED, PageRequest.of(0, limit));
        return toSummaryPage(artworks, size);
    }

    @Override
    public CursorPage<ArtworkSummaryInfo> getTrashArtworks(String memberId, String cursor, int size) {
        int limit = size + 1;
        List<Artwork> artworks = cursor != null
                ? artworkRepository.findByAuthorIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
                        memberId, ArtworkStatus.DELETED, parseCursor(cursor), PageRequest.of(0, limit))
                : artworkRepository.findByAuthorIdAndStatusOrderByCreatedAtDesc(
                        memberId, ArtworkStatus.DELETED, PageRequest.of(0, limit));
        return toSummaryPage(artworks, size);
    }

    @Override
    @Transactional
    public void restoreArtworks(String memberId, List<String> artworkIds) {
        List<Artwork> artworks = artworkRepository.findAllById(artworkIds);
        validateAllFound(artworks, artworkIds);
        // 소유권·휴지통 상태를 먼저 확인한다 — 남의 작품이거나 휴지통에 없는 작품 ID를 섞어 보낸 요청에
        // 개수 제한 오류를 돌려주지 않기 위함이다.
        for (Artwork artwork : artworks) {
            artwork.assertOwner(memberId);
            artwork.assertDeleted();
        }
        // 복구도 보유 작품이 늘어나는 행위라 스타터 제한을 그대로 적용한다(휴지통-R03).
        assertArtworkQuota(memberId, artworks.size());
        for (Artwork artwork : artworks) {
            artwork.restore(imageStatusesOf(artwork.getId()));
        }
        artworkRepository.saveAll(artworks);
        artworks.forEach(a -> eventPublisher.publishEvent(new ArtworkChangedEvent(a.getId())));
    }

    @Override
    @Transactional
    public void permanentlyDeleteArtworks(String memberId, List<String> artworkIds) {
        List<Artwork> artworks = artworkRepository.findAllById(artworkIds);
        validateAllFound(artworks, artworkIds);
        for (Artwork artwork : artworks) {
            artwork.assertOwner(memberId);
            artwork.assertDeleted();
        }
        artworkPurger.purge(artworks);
    }

    @Override
    @Transactional
    public void updatePortfolioInclusion(String artworkId, boolean included) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.updatePortfolioInclusion(included);
        artworkRepository.save(artwork);
    }

    @Override
    public Optional<ArtworkInfo> getArtworkForIndexing(String artworkId) {
        return artworkRepository.findById(artworkId)
                .map(artwork -> {
                    MemberInfo author;
                    try {
                        author = memberService.findById(artwork.getAuthorId());
                    } catch (Exception e) {
                        author = null;
                    }
                    return toInfo(artwork, author);
                });
    }

    @Override
    public CursorPage<ArtworkInfo> getArtworksForReindex(String cursor, int size) {
        int limit = size + 1;
        List<Artwork> artworks = cursor != null
                ? artworkRepository.findByCreatedAtAfterOrderByCreatedAtAsc(parseCursor(cursor), PageRequest.of(0, limit))
                : artworkRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, limit));
        if (artworks.isEmpty()) return CursorPage.empty();

        boolean hasNext = artworks.size() > size;
        List<Artwork> page = hasNext ? artworks.subList(0, size) : artworks;

        Set<String> authorIds = page.stream().map(Artwork::getAuthorId).collect(Collectors.toSet());
        // 배치 조회 — 예전에는 작가마다 findById를 부르고 실패 시 null을 반환했는데,
        // Collectors.toMap이 null 값에 NPE를 던져 작가 한 명의 조회 실패가 페이지 전체를
        // 500으로 만들었다(이슈 #112). 없는 작가는 맵에 담기지 않고 조회 결과가 null이 된다.
        java.util.Map<String, MemberInfo> authorMap = memberService.findAllByIds(authorIds);

        Map<String, ArtworkMedia> mediaByArtwork = ArtworkMedia.loadAll(mediaService,
                page.stream().map(Artwork::getId).toList());
        List<ArtworkInfo> items = page.stream()
                .map(a -> ArtworkMapper.toInfo(a, authorMap.get(a.getAuthorId()), mediaByArtwork.get(a.getId())))
                .toList();

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getCreatedAt().toEpochMilli())
                : null;
        return CursorPage.of(items, nextCursor);
    }

    // 내 작품·휴지통 목록은 등록일 내림차순 단일 커서를 그대로 쓴다(커뮤니티 피드만 정렬 기준이 여러 개다).
    private CursorPage<ArtworkSummaryInfo> toSummaryPage(List<Artwork> artworks, int size) {
        return toSummaryPage(artworks, size, last -> String.valueOf(last.getCreatedAt().toEpochMilli()));
    }

    private CursorPage<ArtworkSummaryInfo> toSummaryPage(List<Artwork> artworks, int size,
                                                         Function<Artwork, String> nextCursorOf) {
        if (artworks.isEmpty()) return CursorPage.empty();

        boolean hasNext = artworks.size() > size;
        List<Artwork> page = hasNext ? artworks.subList(0, size) : artworks;
        String nextCursor = hasNext ? nextCursorOf.apply(page.get(page.size() - 1)) : null;
        return CursorPage.of(toSummaryInfos(page), nextCursor);
    }

    /** 작가 정보·이미지를 붙여 카드 목록으로 바꾼다. 커서 목록과 커뮤니티 오프셋 목록이 함께 쓴다. */
    private List<ArtworkSummaryInfo> toSummaryInfos(List<Artwork> artworks) {
        if (artworks.isEmpty()) return List.of();
        // 작가 정보 일괄 조회 (N+1 완화 — 향후 batch API 추가 예정)
        Set<String> authorIds = artworks.stream().map(Artwork::getAuthorId).collect(Collectors.toSet());
        // 배치 조회 — 예전에는 작가마다 findById를 부르고 실패 시 null을 반환했는데,
        // Collectors.toMap이 null 값에 NPE를 던져 작가 한 명의 조회 실패가 페이지 전체를
        // 500으로 만들었다(이슈 #112). 없는 작가는 맵에 담기지 않고 조회 결과가 null이 된다.
        java.util.Map<String, MemberInfo> authorMap = memberService.findAllByIds(authorIds);

        // 이미지는 media가 갖는다(#193) — 목록은 소유자 ID를 모아 한 번에 읽는다.
        Map<String, ArtworkMedia> mediaByArtwork = ArtworkMedia.loadAll(mediaService,
                artworks.stream().map(Artwork::getId).toList());

        return artworks.stream()
                .map(a -> ArtworkMapper.toSummaryInfo(a, authorMap.get(a.getAuthorId()), mediaByArtwork.get(a.getId())))
                .toList();
    }

    /** 이미지는 media가 갖는다(#193) — 응답을 만들 때마다 읽어 넘긴다. */
    private ArtworkInfo toInfo(Artwork artwork, MemberInfo author) {
        return ArtworkMapper.toInfo(artwork, author, ArtworkMedia.load(mediaService, artwork.getId()));
    }

    private List<ImageProcessingStatus> imageStatusesOf(String artworkId) {
        return mediaService.getAssets(MediaOwnerType.ARTWORK, artworkId).stream()
                .map(MediaAssetInfo::status).map(ArtworkMapper::toImageStatus).toList();
    }

    private Artwork findArtworkById(String artworkId) {
        return artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId));
    }

    /**
     * 스타터 플랜 작품 개수 제한(마이페이지_작가-R20). 프로 플랜은 제한이 없고, 다운그레이드해도
     * 기존 작품은 그대로 유지되며 신규 생성만 막힌다(요금제-R01).
     */
    private void assertArtworkQuota(String memberId, int increment) {
        if (billingService.hasProPlan(memberId)) {
            return;
        }
        // 보유 작품 행에 락을 걸어 개수를 센다 — 락 없는 count만으로는 동시 요청이 같은 개수를 보고
        // 둘 다 통과해 제한을 넘길 수 있다.
        // FAILED는 이미지가 한 장도 안 남아 공개할 수 없는 작품이라 한도에서 뺀다 — 넣어두면 변환 실패
        // 때문에 재업로드가 막혀 사용자가 스스로 빠져나올 수 없다. PROCESSING은 성공할 수 있으므로 센다.
        long owned = artworkRepository.findByAuthorIdAndStatusNotInForUpdate(
                memberId, List.of(ArtworkStatus.DELETED, ArtworkStatus.FAILED)).size();
        if (owned + increment > STARTER_ARTWORK_LIMIT) {
            throw new ArtworkException(ArtworkErrorCode.STARTER_ARTWORK_LIMIT_EXCEEDED,
                    "memberId=" + memberId + ", owned=" + owned + ", increment=" + increment);
        }
    }

    /**
     * 변환 화질 등급(요금제-R03·R04) — 스타터는 웹 감상용으로 축소, 프로는 원본 해상도를 유지한다.
     *
     * <p>이미지를 새로 올리는 시점(업로드·이미지 교체)의 플랜으로 확정한다. 변환은 그때 1회뿐이라
     * 이후 플랜이 바뀌어도 이미 올라간 이미지의 화질은 그대로다(요금제-R01 다운그레이드 정책과 동일한 결).
     */
    private MediaQualityTier qualityTierOf(String memberId) {
        return billingService.hasProPlan(memberId) ? MediaQualityTier.ORIGINAL : MediaQualityTier.WEB;
    }

    /**
     * 언어 세그먼트 필터(로그인-R16) — 뷰어가 보기로 한 언어 중 하나라도 가진 작품만 남긴다.
     *
     * <p>언어를 고른 적 없는 작품(마이그레이션 이전 업로드분)은 항상 노출한다. 필터 도입만으로
     * 기존 작품이 피드에서 통째로 사라지는 것을 막는 폴백이다.
     *
     * <p>{@code languages}는 @ElementCollection이라 join으로 걸면 언어 수만큼 행이 늘어 커서
     * 페이지네이션이 깨진다. EXISTS 서브쿼리로 행 수를 보존한다.
     */
    private Predicate languageSegmentPredicate(Root<Artwork> root, CriteriaQuery<?> query,
                                               CriteriaBuilder cb, List<Language> viewerLanguages) {
        Subquery<String> subquery = query.subquery(String.class);
        Root<Artwork> subRoot = subquery.from(Artwork.class);
        Join<Artwork, Language> languageJoin = subRoot.join("languages");
        subquery.select(subRoot.get("id"))
                .where(cb.and(
                        cb.equal(subRoot.get("id"), root.get("id")),
                        languageJoin.in(viewerLanguages)));
        return cb.or(cb.isEmpty(root.get("languages")), cb.exists(subquery));
    }

    /**
     * 성인 콘텐츠 표시 필터(설정-R10) — 표시 OFF일 때 R18/G18 중 본인 업로드가 아닌 작품을 제외한다.
     * 본인 업로드분은 표시 설정과 무관하게 항상 노출된다(마이페이지_작가-R21).
     */
    private Predicate adultContentPredicate(Root<Artwork> root, CriteriaBuilder cb, String viewerMemberId) {
        Predicate notAdult = cb.not(root.get("ageRating").in(AgeRating.R18, AgeRating.G18));
        Predicate ownUpload = viewerMemberId != null
                ? cb.equal(root.get("authorId"), viewerMemberId)
                : cb.disjunction();
        return cb.or(notAdult, ownUpload);
    }

    /**
     * 게시물 작성·노출 언어 검증(업로드-R30, REQ-020).
     *
     * <p>규칙은 둘이다. <b>주 사용 언어는 플랜과 무관하게 반드시 포함</b>되고, 거기에 언어를 더하는 것이
     * 프로 전용이다. 결과적으로 스타터는 주 사용 언어 1개로 고정되고 프로는 주 언어를 포함한
     * 최대 4개가 된다.
     *
     * <p>주 언어를 빼지 못하게 막는 이유는 세그먼트가 쪼개지는 것을 막기 위해서다. 작가 목록은 계정의
     * 주 언어로, 작품 피드는 작품의 언어로 거르기 때문에, 주 언어를 뺀 작품은 작성자가 노출되는 세그먼트와
     * 다른 세그먼트에만 노출된다 — 로그인-R16이 "계정·게시글"을 한 덩어리로 묶어 정의한 전제가 깨진다.
     * 설정-R14가 같은 성격의 게시물 언어 선택에서 주 언어 해제를 금지한 것과도 같은 결이다.
     *
     * <p>주 사용 언어가 없는 마이그레이션 이전 회원은 포함 검사를 걸 수 없으므로 개수 제한만 적용한다 —
     * 임의의 언어를 강요하면 기존 회원이 업로드 자체를 못 하게 된다.
     */
    /**
     * 클라이언트가 보낸 R2 key가 본인이 발급받은 것인지 확인한다(#190). key는 공개 응답에 그대로 실리므로,
     * 검증하지 않으면 남의 key를 지정 썸네일·첨부로 넣어 그 파일을 지우거나 삭제를 막을 수 있다.
     *
     * <p>{@code alreadyStored}는 이 작품에 이미 저장돼 있던 key다 — 프론트가 수정마다 기존 값을 다시 보내므로
     * 검증 대상에서 뺀다. 그래서 서명이 없던 시절의 key도 계속 수정할 수 있고, 남의 key를 새로 붙이는 것만 막힌다.
     */
    private void assertKeysOwned(String memberId, Collection<String> keys, Collection<String> alreadyStored) {
        List<String> candidates = keys.stream().filter(k -> k != null && !k.isBlank())
                .filter(k -> !alreadyStored.contains(k)).toList();
        Set<String> unowned = mediaService.unownedKeys(memberId, candidates);
        if (!unowned.isEmpty()) {
            throw new ArtworkException(ArtworkErrorCode.UNOWNED_IMAGE_KEY, String.join(", ", unowned));
        }
    }

    /** 이 작품에 이미 저장돼 있는 key — 이미지(media), 지정 썸네일, 자료 첨부. */
    private Set<String> storedKeysOf(Artwork artwork) {
        Set<String> stored = new HashSet<>();
        mediaService.getAssets(MediaOwnerType.ARTWORK, artwork.getId()).stream()
                .map(MediaAssetInfo::originalKey).forEach(stored::add);
        if (artwork.getThumbnailKey() != null) stored.add(artwork.getThumbnailKey());
        artwork.getMaterials().stream().map(Material::getAttachmentKeys).filter(Objects::nonNull)
                .forEach(stored::addAll);
        return stored;
    }

    /** 요청이 담은 모든 R2 key — 이미지, 사용자 지정 썸네일, 자료 첨부. */
    private static List<String> submittedKeys(List<String> imageKeys, String thumbnailKey,
                                              List<MaterialData> materials) {
        List<String> keys = new ArrayList<>();
        if (imageKeys != null) keys.addAll(imageKeys);
        if (thumbnailKey != null) keys.add(thumbnailKey);
        if (materials != null) {
            materials.stream().map(MaterialData::attachmentKeys).filter(Objects::nonNull).forEach(keys::addAll);
        }
        return keys;
    }

    private void assertLanguagesAllowed(String memberId, List<Language> languages) {
        // 저장은 Set이라 중복은 어차피 합쳐진다 — 개수·플랜 판정도 중복 제거 후 값으로 해야 일치한다.
        Set<Language> distinct = languages == null ? Set.of() : new HashSet<>(languages);
        if (distinct.isEmpty() || distinct.size() > MAX_LANGUAGE_COUNT) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_LANGUAGE_COUNT,
                    "languages=" + languages);
        }
        // 개수 초과를 먼저 본다 — 스타터가 주 언어를 포함해 2개를 골랐을 때 "주 언어 누락"이 아니라
        // "다중 선택은 유료"라는 정확한 안내가 나가야 한다.
        if (distinct.size() > 1 && !billingService.hasProPlan(memberId)) {
            throw new ArtworkException(ArtworkErrorCode.MULTI_LANGUAGE_REQUIRES_PRO,
                    "memberId=" + memberId + ", count=" + distinct.size());
        }
        Language primaryLanguage = memberService.findById(memberId).primaryLanguage();
        if (primaryLanguage != null && !distinct.contains(primaryLanguage)) {
            throw new ArtworkException(ArtworkErrorCode.LANGUAGE_NOT_ALLOWED,
                    "memberId=" + memberId + ", primary=" + primaryLanguage + ", requested=" + distinct);
        }
    }

    private void validateAllFound(List<Artwork> found, List<String> requestedIds) {
        if (found.size() != new java.util.HashSet<>(requestedIds).size()) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND);
        }
    }

    private Instant parseCursor(String cursor) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
    }

    private List<Material> toMaterials(List<MaterialData> data) {
        if (data == null) return List.of();
        return data.stream()
                .map(d -> new Material(d.name(), d.targets(), d.customTargets(), d.attachmentKeys(), d.links()))
                .toList();
    }
}
