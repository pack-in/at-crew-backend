package com.atcrew.artwork.internal.infra.storage;

import java.util.List;

public interface ArtworkStoragePort {

    String generatePresignedPutUrl(String key, String contentType);

    void triggerWorker(String artworkId, List<String> imageKeys);

    void deleteFiles(List<String> keys);
}
