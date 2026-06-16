package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.domain.artwork.OrphanedImageKey;
import com.atcrew.artwork.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.artwork.internal.persistence.OrphanedImageKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OrphanImageCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrphanImageCleanupScheduler.class);

    private final OrphanedImageKeyRepository orphanedRepo;
    private final ArtworkStoragePort storagePort;

    OrphanImageCleanupScheduler(OrphanedImageKeyRepository orphanedRepo,
                                ArtworkStoragePort storagePort) {
        this.orphanedRepo = orphanedRepo;
        this.storagePort = storagePort;
    }

    @Scheduled(fixedDelay = 3_600_000)
    void cleanupOrphanedImages() {
        var page = orphanedRepo.findAll(PageRequest.of(0, 100));
        if (page.isEmpty()) return;
        log.info("고아 이미지 정리 시작: batchSize={}", page.getNumberOfElements());
        for (var orphan : page) {
            try {
                storagePort.deleteFiles(orphan.getKeys());
                orphanedRepo.delete(orphan);
            } catch (Exception e) {
                log.error("고아 이미지 삭제 실패 — 다음 배치에 재시도: id={}", orphan.getId(), e);
            }
        }
    }
}
