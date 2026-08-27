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

@Component
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
        stuck.stream().collect(Collectors.groupingBy(a -> new RetryGroup(a.getOwnerType(), a.getOwnerId(),
                        a.getVariantProfile(), a.getQualityTier())))
                .forEach((group, grouped) -> worker.triggerAsync(group.ownerType(), group.ownerId(),
                        grouped.stream().map(a -> a.getOriginalKey()).toList(),
                        group.variantProfile(), group.qualityTier()));
    }
    // 재시도도 최초 업로드와 같은 화질로 처리해야 하므로 화질 등급까지 그룹 키에 넣는다.
    private record RetryGroup(com.atcrew.media.MediaOwnerType ownerType, String ownerId,
                              com.atcrew.media.MediaVariantProfile variantProfile,
                              com.atcrew.media.MediaQualityTier qualityTier) { }
}
