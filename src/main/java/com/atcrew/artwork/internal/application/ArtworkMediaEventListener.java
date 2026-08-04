package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaAssetProcessedEvent;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * media의 이미지 처리 결과를 artwork 로컬 읽기 모델(artwork_images)에 반영한다
 * (docs/design/media-module-design.md §5).
 *
 * <p>READY 전환 판단 자체는 도메인 규칙이라 {@link Artwork#markImageProcessed}에 그대로 남기고
 * 여기서는 위임만 한다 — 조건은 "모든 이미지 DONE"이 아니라 "PENDING이 하나도 없고 DONE이 하나 이상"
 * (부분 실패 허용)이다.
 */
@Component
class ArtworkMediaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ArtworkMediaEventListener.class);

    private final ArtworkRepository artworkRepository;
    private final ApplicationEventPublisher eventPublisher;

    ArtworkMediaEventListener(ArtworkRepository artworkRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    void onMediaAssetProcessed(MediaAssetProcessedEvent event) {
        if (event.ownerType() != MediaOwnerType.ARTWORK) {
            return;
        }
        artworkRepository.findById(event.ownerId()).ifPresentOrElse(artwork -> {
            artwork.markImageProcessed(
                    event.imageKey(),
                    event.thumbKey(),
                    event.thumbAdultKey(),
                    event.originalAvifKey(),
                    event.status() == MediaProcessingStatus.DONE
            );
            artworkRepository.save(artwork);
            // 검색 색인이 썸네일·상태 변경을 반영하도록 재발행 (기존 webhook 경로와 동일)
            eventPublisher.publishEvent(new ArtworkChangedEvent(artwork.getId()));
            log.debug("이미지 처리 결과 반영: artworkId={} imageKey={} status={}",
                    event.ownerId(), event.imageKey(), event.status());
        }, () -> log.warn("이미지 처리 결과의 작품을 찾을 수 없음: artworkId={} imageKey={}",
                event.ownerId(), event.imageKey()));
    }
}
