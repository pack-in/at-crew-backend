package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.domain.view.ArtworkViewEvent;
import com.atcrew.artwork.internal.domain.view.ArtworkViewKind;
import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.ArtworkViewDedupRepository;
import com.atcrew.artwork.internal.persistence.ArtworkViewEventRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 작품 열람 집계(홈-R14) — 동일 열람자의 24시간 이내 반복 열람은 최초 1회만 센다.
 *
 * <p>판정은 member {@code ProfileViewCounter}와 같은 "조건부 UPDATE → 실패 시 INSERT" 순서다. "조회 후 판단"이
 * 아니라 쓰기의 영향 행 수로 판단하므로, 같은 열람자가 동시에 두 번 요청해도 한 번만 집계된다.
 * 유효 열람(FIRST·REVISIT)일 때만 원천 이벤트를 남기고 누적 조회수를 올린다.
 *
 * <p>호출 측 트랜잭션({@code ArtworkServiceImpl#recordView}) 안에서 동작한다.
 */
@Component
class ArtworkViewRecorder {

    static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final ArtworkViewDedupRepository dedupRepository;
    private final ArtworkViewEventRepository eventRepository;
    private final ArtworkRepository artworkRepository;

    ArtworkViewRecorder(ArtworkViewDedupRepository dedupRepository,
                        ArtworkViewEventRepository eventRepository,
                        ArtworkRepository artworkRepository) {
        this.dedupRepository = dedupRepository;
        this.eventRepository = eventRepository;
        this.artworkRepository = artworkRepository;
    }

    void record(String artworkId, ArtworkViewerType viewerType, String viewerKey, Instant now) {
        ArtworkViewKind kind = classify(artworkId, viewerType, viewerKey, now);
        if (kind == null) {
            return; // 24시간 안의 반복 열람
        }
        eventRepository.save(ArtworkViewEvent.record(artworkId, viewerType, viewerKey, kind, now));
        artworkRepository.incrementViewCount(artworkId);
    }

    /** @return 유효 열람이면 그 종류, 24시간 안의 반복 열람이면 null */
    private ArtworkViewKind classify(String artworkId, ArtworkViewerType viewerType, String viewerKey, Instant now) {
        if (dedupRepository.touchIfStale(artworkId, viewerType, viewerKey, now, now.minus(DEDUP_WINDOW)) > 0) {
            return ArtworkViewKind.REVISIT;
        }
        if (dedupRepository.insertIfAbsent(artworkId, viewerType.name(), viewerKey, now) > 0) {
            return ArtworkViewKind.FIRST;
        }
        return null;
    }
}
