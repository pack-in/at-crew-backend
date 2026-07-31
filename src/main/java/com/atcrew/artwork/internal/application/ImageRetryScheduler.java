package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
class ImageRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImageRetryScheduler.class);

    private final ArtworkRepository artworkRepository;
    private final ImageProcessingWorker imageProcessingWorker;

    ImageRetryScheduler(ArtworkRepository artworkRepository,
                        ImageProcessingWorker imageProcessingWorker) {
        this.artworkRepository = artworkRepository;
        this.imageProcessingWorker = imageProcessingWorker;
    }

    @Scheduled(fixedDelay = 300_000)
    void retryStuckImages() {
        Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
        var stuckArtworks = artworkRepository.findByStatusAndUpdatedAtBefore(ArtworkStatus.PROCESSING, threshold);
        if (stuckArtworks.isEmpty()) return;
        log.info("이미지 처리 재시도: count={}", stuckArtworks.size());
        stuckArtworks.forEach(artwork -> {
            var pendingKeys = artwork.getImages().stream()
                    .filter(img -> img.isPending())
                    .map(img -> img.getOriginalKey())
                    .toList();
            if (!pendingKeys.isEmpty()) {
                imageProcessingWorker.triggerAsync(artwork.getId(), pendingKeys);
            }
        });
    }
}
