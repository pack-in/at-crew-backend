package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaCallbackService {
    private final MediaAssetRepository assets;
    private final ApplicationEventPublisher events;
    public MediaCallbackService(MediaAssetRepository assets, ApplicationEventPublisher events) { this.assets = assets; this.events = events; }
    @Transactional
    public void process(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey, String thumbAdultKey,
                 String originalAvifKey, MediaProcessingStatus status) {
        assets.findByOwnerTypeAndOwnerIdAndOriginalKey(ownerType, ownerId, imageKey).ifPresent(asset -> {
            asset.markProcessed(thumbKey, thumbAdultKey, originalAvifKey, status);
            events.publishEvent(new MediaAssetProcessedEvent(ownerType, ownerId, imageKey, thumbKey, thumbAdultKey,
                    originalAvifKey, status));
        });
    }
}
