package com.atcrew.media.internal.infra.storage;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaVariantProfile;
import java.util.List;

public interface ArtworkStoragePort {
    String generatePresignedPutUrl(String key, String contentType);
    void triggerWorker(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                       MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    void deleteFiles(List<String> keys);
}
