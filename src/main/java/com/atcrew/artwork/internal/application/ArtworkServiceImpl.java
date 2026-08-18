package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
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
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import jakarta.persistence.criteria.Predicate;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
class ArtworkServiceImpl implements ArtworkService {

    private static final Logger log = LoggerFactory.getLogger(ArtworkServiceImpl.class);
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    /** 스타터 플랜 보유 작품 상한(마이페이지_작가-R20). */
    private static final int STARTER_ARTWORK_LIMIT = 4;

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
        if (count < 1 || count > 20) {
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
                command.imageKeys(), MediaVariantProfile.STANDARD_WITH_ADULT_BLUR);
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        log.info("작품 업로드 완료: artworkId={} memberId={}", saved.getId(), memberId);
        return ArtworkMapper.toInfo(saved, author);
    }

    @Override
    public ArtworkInfo getArtwork(String artworkId, String viewerMemberId) {
        Artwork artwork = findArtworkById(artworkId);
        switch (artwork.accessFor(viewerMemberId)) {
            case NOT_FOUND -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
            case DELETED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_DELETED, artworkId);
            case PRIVATE -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_PRIVATE, artworkId);
            case BLOCKED -> throw new ArtworkException(ArtworkErrorCode.ARTWORK_BLOCKED, artworkId);
            case ALLOWED -> { }
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
                command.tools(),
                command.workDuration(),
                command.cutCount(),
                command.videoLinks()
        );

        Artwork saved = artworkRepository.save(artwork);
        if (command.imageKeys() != null) {
            mediaService.replaceAndTriggerProcessing(MediaOwnerType.ARTWORK, saved.getId(),
                    command.imageKeys(), MediaVariantProfile.STANDARD_WITH_ADULT_BLUR);
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
    public CursorPage<ArtworkSummaryInfo> getCommunityArtworks(ArtworkField artworkField,
                                                                AgeRating ageRating,
                                                                String cursor, int size) {
        int limit = size + 1;
        Specification<Artwork> spec = buildCommunitySpecification(artworkField, ageRating, cursor);
        List<Artwork> artworks = artworkRepository
                .findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        return toSummaryPage(artworks, size);
    }

    // Mongo Criteria 동적 쿼리 → JPA Specification (docs/design/mariadb-migration-design.md §3.6)
    private Specification<Artwork> buildCommunitySpecification(ArtworkField artworkField,
                                                                AgeRating ageRating, String cursor) {
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
            if (cursor != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), parseCursor(cursor)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
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

    private CursorPage<ArtworkSummaryInfo> toSummaryPage(List<Artwork> artworks, int size) {
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

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getCreatedAt().toEpochMilli())
                : null;
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
