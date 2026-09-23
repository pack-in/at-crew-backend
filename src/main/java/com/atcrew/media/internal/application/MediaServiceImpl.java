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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Collection;
import java.util.Set;

@Service
class MediaServiceImpl implements MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaServiceImpl.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final MediaAssetRepository assets; private final OrphanedMediaKeyRepository orphans;
    private final ArtworkStoragePort storagePort; private final ImageProcessingWorker worker;
    private final MediaKeySigner signer;
    private final PresignRateLimiter rateLimiter;
    MediaServiceImpl(MediaAssetRepository assets, OrphanedMediaKeyRepository orphans, ArtworkStoragePort storagePort,
                     ImageProcessingWorker worker, MediaKeySigner signer, PresignRateLimiter rateLimiter) { this.assets = assets; this.orphans = orphans; this.storagePort = storagePort; this.worker = worker; this.signer = signer; this.rateLimiter = rateLimiter; }

    @Override public boolean tryReservePresign(String memberId, int count) {
        return rateLimiter.tryIssue(memberId, count);
    }

    @Override public Set<String> unownedKeys(String memberId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return Set.of();
        return keys.stream().filter(k -> k != null && !k.isBlank())
                .filter(k -> !signer.isOwnedBy(k, memberId))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
    @Override public List<PresignedUrlInfo> generatePresignedUrls(String memberId, int count, List<String> contentTypes, List<Long> fileSizes) {
        if (memberId == null || memberId.isBlank()) throw new IllegalArgumentException("발급 대상 회원이 필요합니다.");
        if (count < 1 || count > 30) throw new IllegalArgumentException("이미지 개수는 1~30개여야 합니다.");
        if (contentTypes == null || contentTypes.size() != count || contentTypes.stream().anyMatch(t -> !ALLOWED_CONTENT_TYPES.contains(t)))
            throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다.");
        // fileSizes는 선택 입력이다 — 보내지 않는 클라이언트도 계속 받아준다(Worker가 실측으로 다시 거른다).
        if (fileSizes != null && fileSizes.stream().anyMatch(s -> s != null && s > MediaConstraints.MAX_ORIGINAL_BYTES))
            throw new IllegalArgumentException("이미지 용량이 상한을 초과했습니다.");
        // key에 소유자 서명을 넣는다(#190) — 제출 시 이 서명으로 "내가 발급받은 key인가"를 조회 없이 판정한다.
        return contentTypes.stream().map(type -> {
            String uuid = UuidV7Generator.generate();
            String key = "raw/" + signer.sign(memberId, uuid) + "/" + uuid + extensionFor(type);
            return new PresignedUrlInfo(key, storagePort.generatePresignedPutUrl(key, type));
        }).toList();
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
     * 유지되는 행의 ordinal을 확정값(0부터)으로 바로 옮기면 아직 자리를 비우지 않은 다른 행과
     * uk_ma_owner_order가 충돌한다. 한 번에 이 오프셋만큼 밀어 두고 확정한다 — 소유자당 이미지 30장 상한보다
     * 충분히 크다.
     */
    private static final int ORDINAL_STAGING_OFFSET = 1_000;

    @Override @Transactional public List<MediaAssetInfo> syncAssets(MediaOwnerType ownerType, String ownerId,
            List<MediaAssetSpec> desired, MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        validateSpecs(ownerType, ownerId, desired, variantProfile, qualityTier);
        List<MediaAsset> existing = assets.findByOwnerForUpdate(ownerType, ownerId);
        Set<String> desiredKeys = desired.stream().map(MediaAssetSpec::key).collect(Collectors.toSet());

        List<MediaAsset> removed = existing.stream().filter(a -> !desiredKeys.contains(a.getOriginalKey())).toList();
        markOrphaned(keysOf(removed));
        assets.deleteAll(removed);
        assets.flush();

        Map<String, MediaAsset> kept = existing.stream().filter(a -> desiredKeys.contains(a.getOriginalKey()))
                .collect(Collectors.toMap(MediaAsset::getOriginalKey, a -> a));
        kept.values().forEach(a -> a.relocate(a.getOrdinal() + ORDINAL_STAGING_OFFSET, a.getSlotRole()));
        assets.flush();

        List<MediaAsset> result = new ArrayList<>();
        List<String> toTrigger = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            MediaAssetSpec spec = desired.get(i);
            MediaAsset asset = kept.get(spec.key());
            if (asset == null) {
                asset = assets.save(MediaAsset.pending(ownerType, ownerId, i, spec.slotRole(), spec.key(), variantProfile, qualityTier));
                toTrigger.add(spec.key());
            } else {
                asset.relocate(i, spec.slotRole());
                // DONE은 raw가 이미 지워져 다시 트리거하면 FAILED가 된다 — 변환 결과를 그대로 물려받는다.
                if (asset.getProcessingStatus() != MediaProcessingStatus.DONE) toTrigger.add(spec.key());
            }
            result.add(asset);
        }
        assets.flush();
        if (!toTrigger.isEmpty()) triggerAfterCommit(ownerType, ownerId, toTrigger, variantProfile, qualityTier);
        return result.stream().map(MediaServiceImpl::toInfo).toList();
    }

    @Override @Transactional(readOnly = true) public List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId) {
        return assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(ownerType, ownerId).stream()
                .map(MediaServiceImpl::toInfo).toList();
    }
    @Override @Transactional(readOnly = true) public Map<String, List<MediaAssetInfo>> getAssets(MediaOwnerType ownerType, Collection<String> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) return Map.of();
        return assets.findByOwnerTypeAndOwnerIdInOrderByOwnerIdAscOrdinalAsc(ownerType, Set.copyOf(ownerIds)).stream()
                .collect(Collectors.groupingBy(MediaAsset::getOwnerId, Collectors.mapping(MediaServiceImpl::toInfo, Collectors.toList())));
    }
    private static MediaAssetInfo toInfo(MediaAsset a) {
        return new MediaAssetInfo(a.getOriginalKey(), a.getThumbKey(), a.getThumbAdultKey(), a.getOriginalAvifKey(),
                a.getProcessingStatus(), a.getOrdinal(), a.getSlotRole());
    }
    /**
     * 자산 행을 지우면 그 행이 가리키던 파일(원본·변형본)은 고아 큐로 보낸다. 영구 삭제 키 목록은 삭제 시점의 소유자
     * 엔티티로 만들므로, 그 뒤 이 호출 전까지 도착한 콜백이 기록한 변형본은 목록에 없다 — 여기서 넘기지 않으면 추적
     * 기록 없이 R2에 남는다. 호출자가 이미 처리한 key(handledKeys)는 빼고 넘긴다.
     */
    @Override @Transactional public void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId, Collection<String> handledKeys) {
        var rows = assets.findByOwnerForUpdate(ownerType, ownerId);
        Set<String> handled = Set.copyOf(handledKeys);
        markOrphaned(keysOf(rows).stream().filter(k -> k != null && !handled.contains(k)).toList());
        assets.deleteAll(rows);
    }
    @Override public void deleteFiles(List<String> keys) { storagePort.deleteFiles(keys); }
    /** 자산 행이 가리키는 R2 key 전체(원본·변형본 3종). null은 markOrphaned가 거른다. */
    private static List<String> keysOf(List<MediaAsset> rows) {
        return rows.stream().flatMap(a -> Stream.of(a.getOriginalKey(), a.getThumbKey(),
                a.getThumbAdultKey(), a.getOriginalAvifKey())).toList();
    }

    /** 고아 key 적재의 유일한 경로 — null·빈 key는 걸러내고, 남는 것이 없으면 행을 만들지 않는다. */
    @Override @Transactional public void markOrphaned(List<String> keys) { if (keys != null && keys.stream().anyMatch(k -> k != null && !k.isBlank())) orphans.save(OrphanedMediaKey.ofKeys(keys)); }
    // 같은 key를 두 번 넣으면 콜백이 행을 하나로 특정하지 못해 그 소유자의 처리가 영구히 막힌다.
    private static void validateSpecs(MediaOwnerType ownerType, String ownerId, List<MediaAssetSpec> desired,
                                      MediaVariantProfile profile, MediaQualityTier qualityTier) {
        if (ownerType == null || ownerId == null || ownerId.isBlank() || profile == null || qualityTier == null
                || desired == null || desired.size() > 30
                || desired.stream().anyMatch(s -> s == null || s.key() == null || s.key().isBlank())
                || desired.stream().map(MediaAssetSpec::key).distinct().count() != desired.size())
            throw new IllegalArgumentException("유효하지 않은 media asset 요청입니다.");
    }
    // 중복 key는 교체 경로와 같은 이유로 여기서도 막는다 — 같은 소유자에 같은 original_key가 둘이면 콜백이 행을
    // 특정하지 못해 그 소유자의 이미지 처리가 영구히 멈춘다.
    private static void validate(MediaOwnerType ownerType, String ownerId, List<String> imageKeys, MediaVariantProfile profile, MediaQualityTier qualityTier) {
        if (ownerType == null || ownerId == null || ownerId.isBlank() || profile == null || qualityTier == null
                || imageKeys == null || imageKeys.isEmpty() || imageKeys.size() > 30
                || imageKeys.stream().anyMatch(k -> k == null || k.isBlank())
                || imageKeys.stream().distinct().count() != imageKeys.size())
            throw new IllegalArgumentException("유효하지 않은 media asset 요청입니다.");
    }
    private static String extensionFor(String contentType) { return switch (contentType) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new IllegalArgumentException("지원하지 않는 이미지 content type입니다."); }; }
}
