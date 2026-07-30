package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.OrphanedImageKeyRepository;
import com.atcrew.artwork.internal.domain.artwork.OrphanedImageKey;
import com.atcrew.member.MemberDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ArtworkEventListener {

    private static final Logger log = LoggerFactory.getLogger(ArtworkEventListener.class);

    private final ArtworkRepository artworkRepository;
    private final ArtworkStoragePort storagePort;
    private final OrphanedImageKeyRepository orphanedRepo;
    private final ApplicationEventPublisher eventPublisher;

    ArtworkEventListener(ArtworkRepository artworkRepository,
                         ArtworkStoragePort storagePort,
                         OrphanedImageKeyRepository orphanedRepo,
                         ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.storagePort = storagePort;
        this.orphanedRepo = orphanedRepo;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onMemberDeactivated(MemberDeactivatedEvent event) {
        List<Artwork> artworks = artworkRepository.findAllByAuthorId(event.memberId());
        artworks.forEach(Artwork::forcePrivate);
        artworkRepository.saveAll(artworks);
        // ArtworkServiceImpl을 거치지 않는 경로라 여기서 직접 발행 — 검색 색인이 비공개 전환을 놓치지 않도록 함
        artworks.forEach(a -> eventPublisher.publishEvent(new ArtworkChangedEvent(a.getId())));
        log.info("탈퇴 회원 작품 비공개 처리: memberId={} count={}", event.memberId(), artworks.size());
    }

    @Async
    @EventListener
    public void onPermanentlyDeleted(ArtworkPermanentlyDeletedEvent event) {
        try {
            storagePort.deleteFiles(event.allImageKeys());
            log.info("영구 삭제 R2 파일 제거: artworkId={} keyCount={}",
                    event.artworkId(), event.allImageKeys().size());
        } catch (Exception e) {
            log.error("R2 파일 삭제 실패 — orphanedImageKeys에 적재: artworkId={}", event.artworkId(), e);
            if (!event.allImageKeys().isEmpty()) {
                orphanedRepo.save(OrphanedImageKey.ofKeys(event.allImageKeys()));
            }
        }
    }
}
