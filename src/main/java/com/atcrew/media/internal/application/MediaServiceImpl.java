package com.atcrew.media.internal.application;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
class MediaServiceImpl implements MediaService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final MediaAssetRepository assets; private final OrphanedMediaKeyRepository orphans;
    private final ArtworkStoragePort storagePort; private final ImageProcessingWorker worker;
    MediaServiceImpl(MediaAssetRepository assets, OrphanedMediaKeyRepository orphans, ArtworkStoragePort storagePort,
                     ImageProcessingWorker worker) { this.assets = assets; this.orphans = orphans; this.storagePort = storagePort; this.worker = worker; }
    @Override public List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes) {
        if (count < 1 || count > 20) throw new IllegalArgumentException("이미지 개수는 1~20개여야 합니다.");
        if (contentTypes == null || contentTypes.size() != count || contentTypes.stream().anyMatch(t -> !ALLOWED_CONTENT_TYPES.contains(t)))
            throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다.");
        return contentTypes.stream().map(type -> { String key = "raw/" + UuidV7Generator.generate() + extensionFor(type); return new PresignedUrlInfo(key, storagePort.generatePresignedPutUrl(key, type)); }).toList();
    }
    @Override @Transactional public void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
            List<String> imageKeys, MediaVariantProfile variantProfile) {
        validate(ownerType, ownerId, imageKeys, variantProfile);
        for (int i = 0; i < imageKeys.size(); i++) assets.save(MediaAsset.pending(ownerType, ownerId, i, imageKeys.get(i), variantProfile));
        worker.triggerAsync(ownerType, ownerId, imageKeys, variantProfile);
    }
    @Override @Transactional public void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
            List<String> newImageKeys, MediaVariantProfile variantProfile) {
        var previous = assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(ownerType, ownerId);
        // 아직 처리되지 않은 자산은 thumb/avif key가 null이라 List.of로 묶으면 NPE가 난다 — Stream.of로 받아 걸러낸다.
        var oldKeys = previous.stream().flatMap(a -> java.util.stream.Stream.of(a.getOriginalKey(), a.getThumbKey(), a.getThumbAdultKey(), a.getOriginalAvifKey())).filter(k -> k != null && !k.isBlank()).toList();
        if (!oldKeys.isEmpty()) orphans.save(OrphanedMediaKey.ofKeys(oldKeys));
        // 삭제를 flush로 먼저 확정한 뒤 새 행을 넣는다 — 같은 flush에 묶이면 Hibernate가 INSERT를 DELETE보다
        // 먼저 실행해 uk_ma_owner_order와 충돌한다(설계 §2.1이 artwork에서 그대로 옮겨오라고 명시한 2단계 패턴).
        assets.deleteAll(previous);
        assets.flush();
        registerAndTriggerProcessing(ownerType, ownerId, newImageKeys, variantProfile);
    }
    @Override @Transactional(readOnly = true) public List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId) {
        return assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(ownerType, ownerId).stream()
                .map(a -> new MediaAssetInfo(a.getOriginalKey(), a.getThumbKey(), a.getThumbAdultKey(), a.getOriginalAvifKey(), a.getProcessingStatus())).toList();
    }
    @Override @Transactional public void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId) {
        assets.deleteAll(assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(ownerType, ownerId));
    }
    @Override public void deleteFiles(List<String> keys) { storagePort.deleteFiles(keys); }
    @Override @Transactional public void markOrphaned(List<String> keys) { if (keys != null && keys.stream().anyMatch(k -> k != null && !k.isBlank())) orphans.save(OrphanedMediaKey.ofKeys(keys)); }
    private static void validate(MediaOwnerType ownerType, String ownerId, List<String> imageKeys, MediaVariantProfile profile) {
        if (ownerType == null || ownerId == null || ownerId.isBlank() || profile == null || imageKeys == null || imageKeys.isEmpty() || imageKeys.stream().anyMatch(k -> k == null || k.isBlank())) throw new IllegalArgumentException("유효하지 않은 media asset 요청입니다.");
    }
    private static String extensionFor(String contentType) { return switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다."); }; }
}
