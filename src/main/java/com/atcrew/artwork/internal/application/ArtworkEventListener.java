package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.member.MemberDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
class ArtworkEventListener {

    private static final Logger log = LoggerFactory.getLogger(ArtworkEventListener.class);

    private final ArtworkRepository artworkRepository;
    private final MediaService mediaService;
    private final ApplicationEventPublisher eventPublisher;
    private final List<RetainedMediaKeyProvider> retainedMediaKeyProviders;

    ArtworkEventListener(ArtworkRepository artworkRepository,
                         MediaService mediaService,
                         ApplicationEventPublisher eventPublisher,
                         List<RetainedMediaKeyProvider> retainedMediaKeyProviders) {
        this.artworkRepository = artworkRepository;
        this.mediaService = mediaService;
        this.eventPublisher = eventPublisher;
        this.retainedMediaKeyProviders = retainedMediaKeyProviders;
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
        // 보존 판정 자체가 실패하면 전체 키를 고아 큐로 넘긴다 — 스케줄러가 같은 판정을 다시 하므로
        // 보존 대상이 즉시 삭제되는 일은 없다.
        List<String> deletableKeys = event.allImageKeys();
        try {
            Set<String> retainedKeys = retainedKeys(event.allImageKeys());
            deletableKeys = event.allImageKeys().stream().filter(key -> !retainedKeys.contains(key)).toList();
            // 추적 기록을 R2 삭제보다 먼저 남긴다 — 삭제가 중간에 실패해도 보존 key가 미아가 되지 않는다.
            markRetainedAsOrphaned(retainedKeys);
            mediaService.deleteFiles(deletableKeys);
            log.info("영구 삭제 R2 파일 제거: artworkId={} keyCount={} retainedCount={}",
                    event.artworkId(), deletableKeys.size(), retainedKeys.size());
        } catch (Exception e) {
            log.error("R2 파일 삭제 실패 — media 고아 키 정리 큐에 적재: artworkId={}", event.artworkId(), e);
            if (!deletableKeys.isEmpty()) {
                mediaService.markOrphaned(deletableKeys);
            }
        }
        // R2 삭제 성공 여부와 무관하게 media_assets 행은 정리한다 — 영구 삭제된 작품은 더 이상
        // Worker 콜백을 받을 일이 없으므로 메타데이터를 남겨둘 이유가 없다.
        mediaService.deleteAssetsForOwner(MediaOwnerType.ARTWORK, event.artworkId());
    }

    /**
     * 활성 고정형 포트폴리오 스냅샷이 참조 중인 key는 삭제 대상에서 제외한다 — 원본을 영구 삭제해도
     * 스냅샷 이미지는 남아야 한다(docs/design/portfolio-module-design.md §5.6).
     */
    private Set<String> retainedKeys(List<String> allImageKeys) {
        if (allImageKeys.isEmpty() || retainedMediaKeyProviders.isEmpty()) {
            return Set.of();
        }
        Set<String> retained = new HashSet<>();
        for (RetainedMediaKeyProvider provider : retainedMediaKeyProviders) {
            retained.addAll(provider.retainedKeys(allImageKeys));
        }
        return retained;
    }

    /**
     * 보존된 key는 고아 정리 큐에 적재한다(§5.6) — 원본 행이 사라진 뒤로는 이 key를 추적할 수단이
     * 스냅샷뿐이라, 그 스냅샷을 담은 고정형 포트폴리오까지 삭제되면 아무도 모르는 R2 파일로 남는다.
     * 큐에 넣어두면 시간당 배치가 같은 보존 판정을 다시 해 참조가 남아 있는 동안에는 유예하고
     * (OrphanedMediaKey.keepOnly), 참조가 끊기는 순간 정리한다.
     */
    private void markRetainedAsOrphaned(Set<String> retainedKeys) {
        if (retainedKeys.isEmpty()) {
            return;
        }
        mediaService.markOrphaned(List.copyOf(retainedKeys));
    }
}
