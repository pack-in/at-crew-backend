package com.atcrew.media;

import java.util.List;

public interface MediaService {
    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes);
    void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                      MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> newImageKeys,
                                     MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId);
    /** 소유자의 media_assets 행을 전부 제거한다. R2 파일 삭제는 호출자가 별도로 처리한다. */
    void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId);
    void deleteFiles(List<String> keys);
    void markOrphaned(List<String> keys);
}
