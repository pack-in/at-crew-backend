package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.infra.storage.ArtworkStoragePort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ImageProcessingWorker {

    private final ArtworkStoragePort storagePort;

    ImageProcessingWorker(ArtworkStoragePort storagePort) {
        this.storagePort = storagePort;
    }

    @Async
    void triggerAsync(String artworkId, List<String> imageKeys) {
        storagePort.triggerWorker(artworkId, imageKeys);
    }
}
