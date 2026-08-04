package com.atcrew.media.internal.application;
import com.atcrew.media.*;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
class ImageProcessingWorker {
    private final ArtworkStoragePort storagePort;
    ImageProcessingWorker(ArtworkStoragePort storagePort) { this.storagePort = storagePort; }
    @Async void triggerAsync(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                             MediaVariantProfile variantProfile) {
        storagePort.triggerWorker(ownerType, ownerId, imageKeys, variantProfile);
    }
}
