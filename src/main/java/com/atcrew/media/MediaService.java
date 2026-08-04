package com.atcrew.media;

import java.util.List;

public interface MediaService {
    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes);
    void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                      MediaVariantProfile variantProfile);
    void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> newImageKeys,
                                     MediaVariantProfile variantProfile);
    List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId);
    void deleteFiles(List<String> keys);
    void markOrphaned(List<String> keys);
}
