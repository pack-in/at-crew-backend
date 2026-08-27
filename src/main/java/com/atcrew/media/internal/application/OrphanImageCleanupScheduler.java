package com.atcrew.media.internal.application;

import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
class OrphanImageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrphanImageCleanupScheduler.class);
    private final OrphanedMediaKeyRepository orphans; private final ArtworkStoragePort storagePort;
    private final List<RetainedMediaKeyProvider> retainedMediaKeyProviders;
    OrphanImageCleanupScheduler(OrphanedMediaKeyRepository orphans, ArtworkStoragePort storagePort,
                                List<RetainedMediaKeyProvider> retainedMediaKeyProviders) {
        this.orphans = orphans; this.storagePort = storagePort; this.retainedMediaKeyProviders = retainedMediaKeyProviders;
    }
    @Scheduled(fixedDelay = 3_600_000)
    void cleanupOrphanedImages() {
        // 오래 적재된 순으로 처리한다 — 보존 대상은 재판정 시점으로 markedAt이 갱신돼 큐 뒤로 밀린다.
        var page = orphans.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "markedAt")));
        if (page.isEmpty()) return;
        log.info("고아 이미지 정리 시작: batchSize={}", page.getNumberOfElements());
        for (var orphan : page) try {
            // 활성 고정형 포트폴리오 스냅샷이 참조 중인 key는 지우지 않는다
            // (docs/design/portfolio-module-design.md §5.6).
            Set<String> retained = retainedKeys(orphan.getKeys());
            List<String> deletable = orphan.getKeys().stream().filter(k -> !retained.contains(k)).toList();
            if (!deletable.isEmpty()) storagePort.deleteFiles(deletable);
            if (retained.isEmpty()) orphans.delete(orphan);
            else {
                // 보존 대상은 큐에 남겨 다음 배치에서 다시 판정한다 — 포트폴리오가 삭제되면 그때 정리된다.
                orphan.keepOnly(retained);
                orphans.save(orphan);
                log.info("고아 이미지 일부 보존 — 참조 중인 key는 정리 유예: id={} retainedCount={}", orphan.getId(), retained.size());
            }
        }
        catch (Exception e) { log.error("고아 이미지 삭제 실패 — 다음 배치에 재시도: id={}", orphan.getId(), e); }
    }
    private Set<String> retainedKeys(List<String> keys) {
        if (keys.isEmpty() || retainedMediaKeyProviders.isEmpty()) return Set.of();
        Set<String> retained = new HashSet<>();
        for (RetainedMediaKeyProvider provider : retainedMediaKeyProviders) retained.addAll(provider.retainedKeys(keys));
        return retained;
    }
}
