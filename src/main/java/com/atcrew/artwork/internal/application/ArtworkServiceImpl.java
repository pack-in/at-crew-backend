package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.ImageProcessedCallbackCommand;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.MaterialData;
import com.atcrew.artwork.PresignedUrlInfo;
import com.atcrew.artwork.UpdateArtworkCommand;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.artwork.ArtworkImage;
import com.atcrew.artwork.internal.domain.artwork.Material;
import com.atcrew.artwork.internal.domain.artwork.OrphanedImageKey;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.OrphanedImageKeyRepository;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class ArtworkServiceImpl implements ArtworkService {

    private static final Logger log = LoggerFactory.getLogger(ArtworkServiceImpl.class);
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final ArtworkRepository artworkRepository;
    private final OrphanedImageKeyRepository orphanedRepo;
    private final MongoTemplate mongoTemplate;
    private final MemberService memberService;
    private final ArtworkStoragePort storagePort;
    private final ImageProcessingWorker imageProcessingWorker;
    private final ApplicationEventPublisher eventPublisher;

    ArtworkServiceImpl(ArtworkRepository artworkRepository,
                       OrphanedImageKeyRepository orphanedRepo,
                       MongoTemplate mongoTemplate,
                       MemberService memberService,
                       ArtworkStoragePort storagePort,
                       ImageProcessingWorker imageProcessingWorker,
                       ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.orphanedRepo = orphanedRepo;
        this.mongoTemplate = mongoTemplate;
        this.memberService = memberService;
        this.storagePort = storagePort;
        this.imageProcessingWorker = imageProcessingWorker;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes) {
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
        List<PresignedUrlInfo> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String ext = contentTypes.get(i).split("/")[1];
            String key = "raw/" + java.util.UUID.randomUUID() + "." + ext;
            String url = storagePort.generatePresignedPutUrl(key, contentTypes.get(i));
            result.add(new PresignedUrlInfo(key, url));
        }
        return result;
    }

    @Override
    @Transactional
    public ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command) {
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
                command.visibility(),
                command.tools(),
                command.workDuration(),
                command.cutCount(),
                command.videoLinks(),
                materials
        );
        Artwork saved = artworkRepository.save(artwork);
        imageProcessingWorker.triggerAsync(saved.getId(), command.imageKeys());
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        log.info("작품 업로드 완료: artworkId={} memberId={}", saved.getId(), memberId);
        return ArtworkMapper.toInfo(saved, author);
    }

    @Override
    public ArtworkInfo getArtwork(String artworkId, String viewerMemberId) {
        Artwork artwork = findArtworkById(artworkId);
        if (!artwork.isVisibleTo(viewerMemberId)
                && (viewerMemberId == null || !artwork.getAuthorId().equals(viewerMemberId))) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND);
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
            // 이미지 교체: 기존 이미지를 orphaned로 등록하고 새 이미지로 교체
            int newRepIndex = command.representativeImageIndex() != null
                    ? command.representativeImageIndex() : 0;
            List<ArtworkImage> orphaned = artwork.replaceImages(command.imageKeys(), newRepIndex);
            List<OrphanedImageKey> orphanedDocs = orphaned.stream()
                    .map(OrphanedImageKey::from)
                    .toList();
            orphanedRepo.saveAll(orphanedDocs);
        }

        List<Material> materials = command.materials() != null ? toMaterials(command.materials()) : null;
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
                command.videoLinks(),
                materials
        );

        Artwork saved = artworkRepository.save(artwork);
        if (command.imageKeys() != null) {
            imageProcessingWorker.triggerAsync(saved.getId(), command.imageKeys());
        }
        eventPublisher.publishEvent(new ArtworkChangedEvent(saved.getId()));
        MemberInfo author = memberService.findById(memberId);
        return ArtworkMapper.toInfo(saved, author);
    }

    @Override
    @Transactional
    public void updateVisibility(String memberId, String artworkId, Visibility visibility) {
        Artwork artwork = findArtworkById(artworkId);
        artwork.assertOwner(memberId);
        artwork.changeVisibility(visibility);
        artworkRepository.save(artwork);
        eventPublisher.publishEvent(new ArtworkChangedEvent(artworkId));
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
        Criteria criteria = Criteria.where("status").is(ArtworkStatus.READY.name())
                .and("visibility").is(Visibility.PUBLIC.name());
        if (artworkField != null) {
            criteria = criteria.and("artworkField").is(artworkField.name());
        }
        if (ageRating != null) {
            criteria = criteria.and("ageRating").is(ageRating.name());
        }
        if (cursor != null) {
            criteria = criteria.and("createdAt").lt(parseCursor(cursor));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        List<Artwork> artworks = mongoTemplate.find(query, Artwork.class);
        return toSummaryPage(artworks, size);
    }

    @Override
    public CursorPage<ArtworkSummaryInfo> getMyArtworks(String memberId, String cursor, int size) {
        int limit = size + 1;
        Criteria criteria = Criteria.where("authorId").is(memberId)
                .and("status").ne(ArtworkStatus.DELETED.name());
        if (cursor != null) {
            criteria = criteria.and("createdAt").lt(parseCursor(cursor));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        List<Artwork> artworks = mongoTemplate.find(query, Artwork.class);
        return toSummaryPage(artworks, size);
    }

    @Override
    public CursorPage<ArtworkSummaryInfo> getTrashArtworks(String memberId, String cursor, int size) {
        int limit = size + 1;
        Criteria criteria = Criteria.where("authorId").is(memberId)
                .and("status").is(ArtworkStatus.DELETED.name());
        if (cursor != null) {
            criteria = criteria.and("createdAt").lt(parseCursor(cursor));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        List<Artwork> artworks = mongoTemplate.find(query, Artwork.class);
        return toSummaryPage(artworks, size);
    }

    @Override
    @Transactional
    public void restoreArtworks(String memberId, List<String> artworkIds) {
        List<Artwork> artworks = artworkRepository.findAllById(artworkIds);
        validateAllFound(artworks, artworkIds);
        for (Artwork artwork : artworks) {
            artwork.assertOwner(memberId);
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
            List<String> allKeys = artwork.getImages().stream()
                    .flatMap(img -> List.of(
                            img.getOriginalKey(),
                            img.getThumbKey(),
                            img.getThumbAdultKey(),
                            img.getOriginalAvifKey()
                    ).stream())
                    .filter(k -> k != null && !k.isBlank())
                    .toList();
            eventPublisher.publishEvent(new ArtworkPermanentlyDeletedEvent(artwork.getId(), allKeys));
            eventPublisher.publishEvent(new ArtworkChangedEvent(artwork.getId()));
        }
    }

    @Override
    @Transactional
    public void handleImageProcessedCallback(ImageProcessedCallbackCommand command) {
        Artwork artwork = findArtworkById(command.artworkId());
        boolean success = command.status() == ImageProcessingStatus.DONE;
        artwork.markImageProcessed(
                command.imageKey(),
                command.thumbKey(),
                command.thumbAdultKey(),
                command.originalAvifKey(),
                success
        );
        artworkRepository.save(artwork);
        eventPublisher.publishEvent(new ArtworkChangedEvent(command.artworkId()));
        log.debug("이미지 처리 콜백: artworkId={} imageKey={} status={}",
                command.artworkId(), command.imageKey(), command.status());
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
        Criteria criteria = new Criteria();
        if (cursor != null) {
            criteria = criteria.and("createdAt").gt(parseCursor(cursor));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(limit);
        List<Artwork> artworks = mongoTemplate.find(query, Artwork.class);
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
