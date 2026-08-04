package com.atcrew.media.internal.application;

import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OrphanImageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrphanImageCleanupScheduler.class);
    private final OrphanedMediaKeyRepository orphans; private final ArtworkStoragePort storagePort;
    OrphanImageCleanupScheduler(OrphanedMediaKeyRepository orphans, ArtworkStoragePort storagePort) { this.orphans = orphans; this.storagePort = storagePort; }
    @Scheduled(fixedDelay = 3_600_000)
    void cleanupOrphanedImages() {
        var page = orphans.findAll(PageRequest.of(0, 100));
        if (page.isEmpty()) return;
        log.info("고아 이미지 정리 시작: batchSize={}", page.getNumberOfElements());
        for (var orphan : page) try { storagePort.deleteFiles(orphan.getKeys()); orphans.delete(orphan); }
        catch (Exception e) { log.error("고아 이미지 삭제 실패 — 다음 배치에 재시도: id={}", orphan.getId(), e); }
    }
}
