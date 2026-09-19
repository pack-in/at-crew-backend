package com.atcrew.media.internal.application;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Set;

@Service
class MediaServiceImpl implements MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaServiceImpl.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final MediaAssetRepository assets; private final OrphanedMediaKeyRepository orphans;
    private final ArtworkStoragePort storagePort; private final ImageProcessingWorker worker;
    MediaServiceImpl(MediaAssetRepository assets, OrphanedMediaKeyRepository orphans, ArtworkStoragePort storagePort,
                     ImageProcessingWorker worker) { this.assets = assets; this.orphans = orphans; this.storagePort = storagePort; this.worker = worker; }
    @Override public List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes, List<Long> fileSizes) {
        if (count < 1 || count > 30) throw new IllegalArgumentException("이미지 개수는 1~30개여야 합니다.");
        if (contentTypes == null || contentTypes.size() != count || contentTypes.stream().anyMatch(t -> !ALLOWED_CONTENT_TYPES.contains(t)))
            throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다.");
        // fileSizes는 선택 입력이다 — 보내지 않는 클라이언트도 계속 받아준다(Worker가 실측으로 다시 거른다).
        if (fileSizes != null && fileSizes.stream().anyMatch(s -> s != null && s > MediaConstraints.MAX_ORIGINAL_BYTES))
            throw new IllegalArgumentException("이미지 용량이 상한을 초과했습니다.");
        return contentTypes.stream().map(type -> { String key = "raw/" + UuidV7Generator.generate() + extensionFor(type); return new PresignedUrlInfo(key, storagePort.generatePresignedPutUrl(key, type)); }).toList();
    }
    @Override @Transactional public void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
            List<String> imageKeys, MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        validate(ownerType, ownerId, imageKeys, variantProfile, qualityTier);
        for (int i = 0; i < imageKeys.size(); i++) assets.save(MediaAsset.pending(ownerType, ownerId, i, imageKeys.get(i), variantProfile, qualityTier));
        triggerAfterCommit(ownerType, ownerId, imageKeys, variantProfile, qualityTier);
    }

    /**
     * Worker 호출을 호출자 트랜잭션이 커밋된 뒤로 미룬다(#174).
     *
     * <p>예전에는 트랜잭션 안에서 바로 {@code @Async} 트리거를 보냈다. 콜백이 커밋보다 먼저 오면 자산 행이 아직
     * 보이지 않아 콜백이 버려지고(재시도 스케줄러가 10~15분 뒤에야 되살린다), 트리거 뒤 롤백되면 존재하지 않는
     * 소유자의 이미지를 변환해 R2에 고아 파일이 남았다. 외부 호출은 되돌릴 수 없으므로 커밋이 확정된 뒤에만 보낸다.
     *
     * <p>두 진입점이 모두 {@code @Transactional}이라 운영 경로에서는 항상 커밋 뒤로 미뤄진다. 트랜잭션 없이 불리는
     * 경우(단위 테스트 등)에만 바로 보낸다. 재시도 스케줄러는 이 메서드를 거치지 않고 worker를 직접 부른다.
     *
     * <p>afterCommit에서 던진 예외는 이미 커밋된 요청의 호출자에게 그대로 전파된다(데이터는 저장됐는데 500).
     * 배포 종료 중 {@code @Async} 제출이 거부되는 경우가 그렇다 — 잡아서 남기고, 자산이 PENDING으로 남아 있으므로
     * 복구는 재시도 스케줄러(10분 넘은 PENDING 재트리거)에 맡긴다.
     */
    private void triggerAfterCommit(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                    MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.triggerAsync(ownerType, ownerId, imageKeys, variantProfile, qualityTier);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    worker.triggerAsync(ownerType, ownerId, imageKeys, variantProfile, qualityTier);
                } catch (RuntimeException e) {
                    log.warn("커밋 뒤 이미지 처리 트리거 실패 — 재시도 스케줄러가 다시 보낸다: ownerType={} ownerId={} count={}",
                            ownerType, ownerId, imageKeys.size(), e);
                }
            }
        });
    }
    /**
     * 이미지 목록 교체. 새 목록에 남는 key는 처리 결과를 그대로 넘겨받고(다시 트리거하지 않는다), 빠진 key의 파일만
     * 고아 큐에 넣고, 새로 들어온 key만 트리거한다.
     *
     * <p>예전에는 남는 key까지 고아로 넘기고 전부 다시 트리거했다. 정리 배치가 남긴 이미지 파일을 지웠고, Worker는
     * 변환을 마치면 raw를 지우므로 재변환은 "원본 없음"으로 FAILED가 됐다(PR #188 코드 리뷰).
     */
    @Override @Transactional public void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
            List<String> newImageKeys, MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        validate(ownerType, ownerId, newImageKeys, variantProfile, qualityTier);
        var previous = assets.findByOwnerForUpdate(ownerType, ownerId);
        var previousByKey = new java.util.HashMap<String, MediaAsset>();
        previous.forEach(a -> previousByKey.putIfAbsent(a.getOriginalKey(), a));
        var kept = new java.util.HashSet<>(newImageKeys);
        markOrphaned(keysOf(previous.stream().filter(a -> !kept.contains(a.getOriginalKey())).toList()));
        // 삭제를 flush로 먼저 확정한 뒤 새 행을 넣는다 — 같은 flush에 묶이면 Hibernate가 INSERT를 DELETE보다
        // 먼저 실행해 uk_ma_owner_order와 충돌한다(설계 §2.1이 artwork에서 그대로 옮겨오라고 명시한 2단계 패턴).
        assets.deleteAll(previous);
        assets.flush();
        var toTrigger = new java.util.ArrayList<String>();
        for (int i = 0; i < newImageKeys.size(); i++) {
            String key = newImageKeys.get(i);
            MediaAsset carried = previousByKey.get(key);
            if (carried != null) {
                assets.save(MediaAsset.carriedOver(carried, i));
            } else {
                assets.save(MediaAsset.pending(ownerType, ownerId, i, key, variantProfile, qualityTier));
                toTrigger.add(key);
            }
        }
        if (!toTrigger.isEmpty()) {
            triggerAfterCommit(ownerType, ownerId, toTrigger, variantProfile, qualityTier);
        }
    }
    @Override @Transactional(readOnly = true) public List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId) {
        return assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(ownerType, ownerId).stream()
                .map(a -> new MediaAssetInfo(a.getOriginalKey(), a.getThumbKey(), a.getThumbAdultKey(), a.getOriginalAvifKey(), a.getProcessingStatus())).toList();
    }
    /**
     * 자산 행을 지우면 그 행이 가리키던 파일(원본·변형본)은 고아 큐로 보낸다. 영구 삭제 키 목록은 삭제 시점의 소유자
     * 엔티티로 만들므로, 그 뒤 이 호출 전까지 도착한 콜백이 기록한 변형본은 목록에 없다 — 여기서 넘기지 않으면 추적
     * 기록 없이 R2에 남는다. 이미 지운 key가 다시 들어와도 정리 배치가 보존 판정 후 다시 지울 뿐이라 해가 없다.
     */
    @Override @Transactional public void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId) {
        var rows = assets.findByOwnerForUpdate(ownerType, ownerId);
        markOrphaned(keysOf(rows));
        assets.deleteAll(rows);
    }
    @Override public void deleteFiles(List<String> keys) { storagePort.deleteFiles(keys); }
    /** 자산 행이 가리키는 R2 key 전체(원본·변형본 3종). null은 markOrphaned가 거른다. */
    private static List<String> keysOf(List<MediaAsset> rows) {
        return rows.stream().flatMap(a -> java.util.stream.Stream.of(a.getOriginalKey(), a.getThumbKey(),
                a.getThumbAdultKey(), a.getOriginalAvifKey())).toList();
    }

    /** 고아 key 적재의 유일한 경로 — null·빈 key는 걸러내고, 남는 것이 없으면 행을 만들지 않는다. */
    @Override @Transactional public void markOrphaned(List<String> keys) { if (keys != null && keys.stream().anyMatch(k -> k != null && !k.isBlank())) orphans.save(OrphanedMediaKey.ofKeys(keys)); }
    private static void validate(MediaOwnerType ownerType, String ownerId, List<String> imageKeys, MediaVariantProfile profile, MediaQualityTier qualityTier) {
        if (ownerType == null || ownerId == null || ownerId.isBlank() || profile == null || qualityTier == null || imageKeys == null || imageKeys.isEmpty() || imageKeys.stream().anyMatch(k -> k == null || k.isBlank())) throw new IllegalArgumentException("유효하지 않은 media asset 요청입니다.");
    }
    private static String extensionFor(String contentType) { return switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다."); }; }
}
