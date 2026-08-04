package com.atcrew.media.internal.application;

import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

// artwork에 같은 단순명의 클래스가 남아 있는 동안의 빈 이름 충돌 회피 — artwork 정리 후에는 불필요.
@Component("mediaImageRetryScheduler")
class ImageRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ImageRetryScheduler.class);
    private final MediaAssetRepository assets; private final ImageProcessingWorker worker;
    ImageRetryScheduler(MediaAssetRepository assets, ImageProcessingWorker worker) { this.assets = assets; this.worker = worker; }
    @Scheduled(fixedDelay = 300_000)
    void retryStuckAssets() {
        var stuck = assets.findByProcessingStatusAndUpdatedAtBefore(MediaProcessingStatus.PENDING,
                Instant.now().minus(10, ChronoUnit.MINUTES));
        if (stuck.isEmpty()) return;
        log.info("이미지 처리 재시도: count={}", stuck.size());
        stuck.stream().collect(Collectors.groupingBy(a -> new RetryGroup(a.getOwnerType(), a.getOwnerId(), a.getVariantProfile())))
                .forEach((group, grouped) -> worker.triggerAsync(group.ownerType(), group.ownerId(),
                        grouped.stream().map(a -> a.getOriginalKey()).toList(), group.variantProfile()));
    }
    private record RetryGroup(com.atcrew.media.MediaOwnerType ownerType, String ownerId,
                              com.atcrew.media.MediaVariantProfile variantProfile) { }
}
