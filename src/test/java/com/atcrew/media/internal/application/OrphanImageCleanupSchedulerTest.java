package com.atcrew.media.internal.application;

import com.atcrew.media.RetainedMediaKeyProvider;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 고아 이미지 정리가 활성 고정형 포트폴리오 스냅샷 참조 key를 건너뛰는지 검증한다
 * (docs/design/portfolio-module-design.md §5.6 — 원본 이미지 교체 시 데이터 유실 방지).
 */
class OrphanImageCleanupSchedulerTest {

    private final OrphanedMediaKeyRepository orphans = mock(OrphanedMediaKeyRepository.class);
    private final ArtworkStoragePort storagePort = mock(ArtworkStoragePort.class);
    private final RetainedMediaKeyProvider retainedMediaKeyProvider = mock(RetainedMediaKeyProvider.class);
    private final OrphanImageCleanupScheduler scheduler =
            new OrphanImageCleanupScheduler(orphans, storagePort, List.of(retainedMediaKeyProvider));

    @Test
    void 참조되지_않는_키는_삭제하고_행을_제거한다() {
        OrphanedMediaKey orphan = givenOrphan("raw/old.png", "thumb/old.avif");

        scheduler.cleanupOrphanedImages();

        verify(storagePort).deleteFiles(List.of("raw/old.png", "thumb/old.avif"));
        verify(orphans).delete(orphan);
    }

    @Test
    void 스냅샷이_참조_중인_키는_정리하지_않고_큐에_남긴다() {
        OrphanedMediaKey orphan = givenOrphan("raw/old.png", "thumb/old.avif");
        when(retainedMediaKeyProvider.retainedKeys(anyCollection())).thenReturn(Set.of("thumb/old.avif"));

        scheduler.cleanupOrphanedImages();

        verify(storagePort).deleteFiles(List.of("raw/old.png"));
        verify(orphans, never()).delete(orphan);
        verify(orphans).save(orphan);
        assertThat(orphan.getKeys()).containsExactly("thumb/old.avif");
    }

    @Test
    void 모든_키가_참조_중이면_삭제를_아예_요청하지_않는다() {
        OrphanedMediaKey orphan = givenOrphan("raw/old.png");
        when(retainedMediaKeyProvider.retainedKeys(anyCollection())).thenReturn(Set.of("raw/old.png"));

        scheduler.cleanupOrphanedImages();

        verify(storagePort, never()).deleteFiles(anyList());
        verify(orphans, never()).delete(orphan);
        assertThat(orphan.getKeys()).containsExactly("raw/old.png");
    }

    private OrphanedMediaKey givenOrphan(String... keys) {
        OrphanedMediaKey orphan = OrphanedMediaKey.ofKeys(List.of(keys));
        when(orphans.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orphan)));
        return orphan;
    }
}
