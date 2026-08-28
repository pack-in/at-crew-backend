package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkSort;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.MaterialData;
import com.atcrew.artwork.PresignedUrlInfo;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.ArtworkPortfolioSelectionRequested;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.artwork.Material;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.billing.BillingService;
import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
class ArtworkServiceImpl implements ArtworkService {

    private static final Logger log = LoggerFactory.getLogger(ArtworkServiceImpl.class);
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    /** 스타터 플랜 보유 작품 상한(마이페이지_작가-R20). */
    private static final int STARTER_ARTWORK_LIMIT = 4;
    // 게시물 언어 칩 4종(로그인-R19) — 프로도 전체 선택이 상한이다
    private static final int MAX_LANGUAGE_COUNT = 4;

    private final ArtworkRepository artworkRepository;
    private final MemberService memberService;
    private final MediaService mediaService;
    private final BillingService billingService;
    private final ApplicationEventPublisher eventPublisher;

    ArtworkServiceImpl(ArtworkRepository artworkRepository,
                       MemberService memberService,
                       MediaService mediaService,
                       BillingService billingService,
                       ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.memberService = memberService;
        this.mediaService = mediaService;
        this.billingService = billingService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes) {
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
        return mediaService.generatePresignedUrls(count, contentTypes).stream()
                .map(info -> new PresignedUrlInfo(info.key(), info.uploadUrl()))
                .toList();
    }

    @Override
    @Transactional
    public ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command) {
        assertArtworkQuota(memberId, 1);
        assertLanguagesAllowed(memberId, command.languages());
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
        // 이미지 처리 트리거(외부 Worker 호출, 롤백 불가)보다 먼저 검증을 끝내려고 순서를 앞에 뒀다.
        eventPublisher.publishEvent(new ArtworkPortfolioSelectionRequested(
                memberId, saved.getId(), command.portfolioIds()));
        mediaService.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, saved.getId(),
                command.imageKeys(), MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, qualityTierOf(memberId));
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        log.info("작품 업로드 완료: artworkId={} memberId={}", saved.getId(), memberId);
        return ArtworkMapper.toInfo(saved, author);
    }

    @Override
    @Transactional
    public ArtworkInfo getArtwork(String artworkId, String viewerMemberId) {
        Artwork artwork = findArtworkById(artworkId);
        switch (artwork.accessFor(viewerMemberId)) {
            case NOT_FOUND -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
            case DELETED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_DELETED, artworkId);
            case PRIVATE -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_PRIVATE, artworkId);
            case BLOCKED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_BLOCKED, artworkId);
            case ALLOWED -> { }
        }
        // 조회순 정렬용 집계(이슈 #78) — 접근이 허용된 뒤에만, 본인 조회는 빼고 센다. dedup은 두지 않는다
        // (recruit 구인글과 동일 정책). 비로그인 조회도 남의 조회이므로 함께 센다.
        if (!artwork.getAuthorId().equals(viewerMemberId)) {
            artworkRepository.incrementViewCount(artworkId);
        }
        MemberInfo author = memberService.findById(artwork.getAuthorId());
        return ArtworkMapper.toInfo(artwork, author);
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

        if (command.imageKeys() != null) {
            replaceImages(artwork, command.imageKeys(), command.representativeImageIndex());
        }

        if (command.materials() != null) {
            replaceMaterials(artwork, toMaterials(command.materials()));
        }

        artwork.updateDetails(
                command.title(),
                command.description(),
                command.imageLayoutType(),
                command.imageKeys() == null ? command.representativeImageIndex() : null,
                command.thumbnailKey(),
                command.artworkField(),
                command.creativeType(),
                command.roles(),
                command.genres(),
                command.tags(),
                command.ageRating(),
                command.languages(),
                command.tools(),
                command.workDuration(),
                command.cutCount(),
                command.videoLinks()
        );

        Artwork saved = artworkRepository.save(artwork);
        if (command.imageKeys() != null) {
            mediaService.replaceAndTriggerProcessing(MediaOwnerType.ARTWORK, saved.getId(),
                    command.imageKeys(), MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, qualityTierOf(memberId));
        }
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        return ArtworkMapper.toInfo(saved, author);
    }

    // 이미지 교체 — 기존 행 삭제를 saveAndFlush로 먼저 확정한 뒤 새 행을 붙인다.
    // Hibernate가 같은 flush에서 신규 INSERT를 기존 DELETE보다 먼저 실행해
    // uk_ai_order(artwork_id, ordinal) 유니크 제약과 충돌하는 것을 막기 위함
    // (docs/design/mariadb-migration-design.md §3.3.2 RefreshToken과 동일 계열의 함정, 이번 전환에서 신규 발견).
    // 교체로 버려지는 R2 key의 고아 처리는 mediaService.replaceAndTriggerProcessing이 담당한다.
    private void replaceImages(Artwork artwork, List<String> newImageKeys, Integer representativeImageIndex) {
        artwork.detachImages();
        artworkRepository.saveAndFlush(artwork);
        int newRepIndex = representativeImageIndex != null ? representativeImageIndex : 0;
        artwork.attachImages(newImageKeys, newRepIndex);
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
    public CursorPage<ArtworkSummaryInfo> getCommunityArtworks(ArtworkField artworkField,
                                                                AgeRating ageRating,
                                                                List<Language> viewerLanguages,
                                                                ArtworkSort sort,
                                                                String cursor, int size,
                                                                String viewerMemberId,
                                                                boolean viewerAdultContentVisible) {
        ArtworkSort resolvedSort = sort != null ? sort : ArtworkSort.LATEST;
        int limit = size + 1;
        Specification<Artwork> spec =
                buildCommunitySpecification(artworkField, ageRating, viewerLanguages, resolvedSort, cursor,
                        viewerMemberId, viewerAdultContentVisible);
        List<Artwork> artworks = artworkRepository
                .findAll(spec, PageRequest.of(0, limit, sortOf(resolvedSort)))
                .getContent();
        return toSummaryPage(artworks, size, last -> communityCursorOf(last, resolvedSort));
    }

    // Mongo Criteria 동적 쿼리 → JPA Specification (docs/design/mariadb-migration-design.md §3.6)
    private Specification<Artwork> buildCommunitySpecification(ArtworkField artworkField,
                                                                AgeRating ageRating,
                                                                List<Language> viewerLanguages,
                                                                ArtworkSort sort, String cursor,
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
            if (cursor != null) {
                predicates.add(communityCursorPredicate(root, cb, sort, cursor));
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

    /**
     * 다음 페이지 커서 — {@code "정렬값_작품ID"}. 정렬 키와 tiebreaker를 함께 실어야 정렬값이 같은
     * 구간의 중간에서도 이어받을 지점을 정확히 지목할 수 있다(member 프로필 검색의 rank 커서와 같은 형태).
     *
     * <p>등록일은 밀리초가 아니라 <b>마이크로초</b>로 싣는다. DB 컬럼이 DATETIME(6)이라 밀리초로 자르면
     * 같은 밀리초 안의 작품이 커서와 정확히 같은 값이 되지 못해 다음 페이지에서 통째로 누락된다.
     */
    private String communityCursorOf(Artwork last, ArtworkSort sort) {
        long sortValue = switch (sort) {
            case LATEST, OLDEST -> toEpochMicros(last.getCreatedAt());
            case VIEW_COUNT -> last.getViewCount();
            case BOOKMARK_COUNT -> last.getBookmarkCount();
        };
        return sortValue + "_" + last.getId();
    }

    /** 커서 이후 구간 조건 — 정렬 방향에 따라 부등호가 뒤집힌다. */
    private Predicate communityCursorPredicate(Root<Artwork> root, CriteriaBuilder cb,
                                               ArtworkSort sort, String cursor) {
        CommunityCursor c = parseCommunityCursor(cursor);
        return switch (sort) {
            case LATEST -> keysetPredicate(cb, root.get("createdAt"), fromEpochMicros(c.sortValue()),
                    root.get("id"), c.artworkId(), false);
            case OLDEST -> keysetPredicate(cb, root.get("createdAt"), fromEpochMicros(c.sortValue()),
                    root.get("id"), c.artworkId(), true);
            case VIEW_COUNT -> keysetPredicate(cb, root.get("viewCount"), c.sortValue(),
                    root.get("id"), c.artworkId(), false);
            case BOOKMARK_COUNT -> keysetPredicate(cb, root.get("bookmarkCount"), c.sortValue(),
                    root.get("id"), c.artworkId(), false);
        };
    }

    /**
     * 튜플 비교 {@code (정렬키, id) < (커서정렬키, 커서id)}를 표준 SQL 형태로 편다(오름차순이면 부등호를 뒤집는다).
     * 단순 AND로 묶으면(정렬키 &lt; 커서 AND id &lt; 커서id) 정렬값이 커서와 같은 나머지 행이 통째로 사라진다.
     */
    private <Y extends Comparable<? super Y>> Predicate keysetPredicate(
            CriteriaBuilder cb, Path<Y> sortPath, Y sortValue,
            Path<String> idPath, String artworkId, boolean ascending) {
        if (ascending) {
            return cb.or(
                    cb.greaterThan(sortPath, sortValue),
                    cb.and(cb.equal(sortPath, sortValue), cb.greaterThan(idPath, artworkId)));
        }
        return cb.or(
                cb.lessThan(sortPath, sortValue),
                cb.and(cb.equal(sortPath, sortValue), cb.lessThan(idPath, artworkId)));
    }

    private record CommunityCursor(
            long sortValue,   // 정렬 키 값 (등록일은 epoch 마이크로초, 조회수·북마크 수는 그 값 그대로)
            String artworkId  // tiebreaker로 쓰는 마지막 작품 ID
    ) {
    }

    // 작품 ID(UUID)에는 '_'가 없으므로 첫 구분자 하나로 정확히 둘로 나뉜다.
    private CommunityCursor parseCommunityCursor(String cursor) {
        int separator = cursor.indexOf('_');
        if (separator < 0 || separator == cursor.length() - 1) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
        try {
            return new CommunityCursor(
                    Long.parseLong(cursor.substring(0, separator)),
                    cursor.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
    }

    private long toEpochMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private Instant fromEpochMicros(long micros) {
        return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                Math.floorMod(micros, 1_000_000L) * 1_000L);
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
            artwork.restore();
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
        artworkRepository.deleteAll(artworks);
        for (Artwork artwork : artworks) {
            eventPublisher.publishEvent(new ArtworkPermanentlyDeletedEvent(artwork.getId(), allImageKeys(artwork)));
            eventPublisher.publishEvent(new ArtworkChangedEvent(artwork.getId()));
        }
    }

    /**
     * 영구 삭제 대상 R2 key 전체 — 이미지 4종에 <b>사용자 지정 썸네일 key</b>까지 포함한다.
     *
     * <p>지정 썸네일은 이미지 처리 대상이 아니라 media_assets에 행이 없어, 여기서 빠지면 어디서도
     * 지워지지 않고 R2에 남는다. 더 중요한 것은 고정형 스냅샷 보존 판정이 이 key로 스냅샷을 찾는다는
     * 점이다({@code PortfolioItemSnapshot.thumb_key}) — 후보 목록에 없으면 스냅샷이 매칭되지 않아
     * 그 스냅샷이 참조 중인 상세 이미지까지 삭제된다(docs/design/portfolio-module-design.md §5.6).
     */
    private List<String> allImageKeys(Artwork artwork) {
        Stream<String> imageKeys = artwork.getImages().stream()
                .flatMap(img -> Stream.of(
                        img.getOriginalKey(),
                        img.getThumbKey(),
                        img.getThumbAdultKey(),
                        img.getOriginalAvifKey()
                ));
        return Stream.concat(imageKeys, Stream.of(artwork.getThumbnailKey()))
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
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
                    return ArtworkMapper.toInfo(artwork, author);
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
        java.util.Map<String, MemberInfo> authorMap = authorIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try { return memberService.findById(id); } catch (Exception e) { return null; }
                        }
                ));

        List<ArtworkInfo> items = page.stream()
                .map(a -> ArtworkMapper.toInfo(a, authorMap.get(a.getAuthorId())))
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

        // 작가 정보 일괄 조회 (N+1 완화 — 향후 batch API 추가 예정)
        Set<String> authorIds = page.stream().map(Artwork::getAuthorId).collect(Collectors.toSet());
        java.util.Map<String, MemberInfo> authorMap = authorIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try { return memberService.findById(id); } catch (Exception e) { return null; }
                        }
                ));

        List<ArtworkSummaryInfo> items = page.stream()
                .map(a -> ArtworkMapper.toSummaryInfo(a, authorMap.get(a.getAuthorId())))
                .toList();

        String nextCursor = hasNext ? nextCursorOf.apply(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
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
        long owned = artworkRepository.findByAuthorIdAndStatusNotForUpdate(memberId, ArtworkStatus.DELETED).size();
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
                .map(d -> new Material(d.name(), d.targets(), d.attachmentKeys(), d.links()))
                .toList();
    }
}
