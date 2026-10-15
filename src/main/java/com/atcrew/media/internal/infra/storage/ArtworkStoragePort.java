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
    /**
     * 이미 처리된 카드 썸네일로 OG 이미지만 만들게 한다(백필). raw는 지워졌으므로 Worker는 썸네일 두 장을 직접 읽고,
     * 결과는 {@code /internal/media/images/og-processed}로 돌려준다. {@code imageKey}는 콜백이 자산을 찾는 식별자다.
     */
    void triggerOgWorker(MediaOwnerType ownerType, String ownerId, String imageKey, String thumbKey,
                         String thumbAdultKey);
    void deleteFiles(List<String> keys);

    /**
     * {@code prefix} 아래에서 {@code modifiedBefore} 이전에 올라온 객체 key — 등록되지 않은 원본을 찾는 데 쓴다(#216).
     *
     * @param limit 한 번에 가져올 최대 개수
     */
    List<String> listKeys(String prefix, Instant modifiedBefore, int limit);
}
