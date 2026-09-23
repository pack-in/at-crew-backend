package com.atcrew.media.internal.infra.storage;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaVariantProfile;
import java.time.Instant;
import java.util.List;

public interface ArtworkStoragePort {
    String generatePresignedPutUrl(String key, String contentType);
    void triggerWorker(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                       MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    void deleteFiles(List<String> keys);

    /**
     * {@code prefix} 아래에서 {@code modifiedBefore} 이전에 올라온 객체 key — 등록되지 않은 원본을 찾는 데 쓴다(#216).
     *
     * @param limit 한 번에 가져올 최대 개수
     */
    List<String> listKeys(String prefix, Instant modifiedBefore, int limit);
}
