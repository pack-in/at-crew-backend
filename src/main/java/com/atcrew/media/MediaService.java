package com.atcrew.media;

import java.util.Collection;
import java.util.List;

public interface MediaService {
    /**
     * @param fileSizes 업로드할 원본의 바이트 크기. 클라이언트가 보내지 않으면 null이며 이때 크기 검사는
     *                  건너뛴다 — 신고값을 믿는 사전 검사라 Worker의 실측 검사를 대체하지 않는다.
     */
    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes, List<Long> fileSizes);
    void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                      MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> newImageKeys,
                                     MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId);
    /**
     * 소유자의 media_assets 행을 전부 제거하고, 행이 가리키던 파일 중 {@code handledKeys}에 없는 것을 고아 큐로 넘긴다.
     * {@code handledKeys}는 호출자가 이미 지웠거나 고아 큐에 넣은 key다 — 같은 key를 두 번 적재하지 않기 위해 받는다.
     */
    void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId, Collection<String> handledKeys);
    void deleteFiles(List<String> keys);
    void markOrphaned(List<String> keys);
}
