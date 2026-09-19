package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaServiceImplTest {
    private final ArtworkStoragePort storage = mock(ArtworkStoragePort.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final ImageProcessingWorker worker = mock(ImageProcessingWorker.class);
    private final OrphanedMediaKeyRepository orphans = mock(OrphanedMediaKeyRepository.class);
    private final MediaService service = new MediaServiceImpl(assets, orphans, storage, worker);

    @Test void deleteAssetsForOwnerRemovesAllMatchingRows() {
        var existing = List.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL));
        when(assets.findByOwnerForUpdate(MediaOwnerType.ARTWORK, "artwork-1")).thenReturn(existing);

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1", List.of());

        verify(assets).deleteAll(existing);
        // 지운 행이 가리키던 파일은 고아 큐로 간다 — 영구 삭제 이벤트 뒤에 도착한 콜백이 남긴 변형본을 놓치지 않는다.
        var orphaned = ArgumentCaptor.forClass(OrphanedMediaKey.class);
        verify(orphans).save(orphaned.capture());
        assertThat(orphaned.getValue().getKeys()).containsExactly("raw/1.jpg");
    }

    @Test void 호출자가_처리한_키는_고아_큐에_다시_넣지_않는다() {
        var processed = MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);
        var late = MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 1, "raw/2.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);
        when(assets.findByOwnerForUpdate(MediaOwnerType.ARTWORK, "artwork-1")).thenReturn(List.of(processed, late));

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1", Set.of("raw/1.jpg"));

        var orphaned = ArgumentCaptor.forClass(OrphanedMediaKey.class);
        verify(orphans).save(orphaned.capture());
        assertThat(orphaned.getValue().getKeys()).containsExactly("raw/2.jpg");
    }

    @Test void 남은_키를_호출자가_모두_처리했으면_고아_행을_만들지_않는다() {
        var processed = MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL);
        when(assets.findByOwnerForUpdate(MediaOwnerType.ARTWORK, "artwork-1")).thenReturn(List.of(processed));

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1", Set.of("raw/1.jpg"));

        verify(orphans, never()).save(any());
        verify(assets).deleteAll(List.of(processed));
    }

    @Test void presignAcceptsOneToThirtySupportedImageTypes() {
        when(storage.generatePresignedPutUrl(anyString(), eq("image/jpeg"))).thenReturn("https://upload.example");
        var urls = service.generatePresignedUrls(1, List.of("image/jpeg"), null);
        assertThat(urls).hasSize(1);
        assertThat(urls.getFirst().key()).startsWith("raw/").endsWith(".jpg");
        assertThat(urls.getFirst().uploadUrl()).isEqualTo("https://upload.example");
    }

    @Test void 화질_등급은_저장과_worker_트리거에_모두_전달된다() {
        service.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.WEB);

        var saved = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(assets).save(saved.capture());
        assertThat(saved.getValue().getQualityTier()).isEqualTo(MediaQualityTier.WEB);
        verify(worker).triggerAsync(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.WEB);
    }

    @Test void 화질_등급이_없으면_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                        MediaVariantProfile.STANDARD, null));
    }

    @Test void presignRejectsCountsOutsideOneToThirtyAndUnsupportedContentTypes() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(0, List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(31, java.util.Collections.nCopies(31, "image/jpeg"), null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(1, List.of("image/gif"), null));
    }

    // 소비 모듈(artwork·recruit)이 자체 에러코드로 먼저 거르지만, media 자체도 상한을 지켜야
    // 다른 소비자가 검증을 빠뜨렸을 때 그대로 통과하지 않는다.
    @Test void presignRejectsFileSizesOverLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(1, List.of("image/jpeg"),
                List.of(MediaConstraints.MAX_ORIGINAL_BYTES + 1)));
    }

    // 트랜잭션 안에서 불리면 Worker 호출은 커밋 뒤로 미룬다(#174). 커밋 전에 나가면 콜백이 커밋보다 먼저 와서
    // 버려지거나, 롤백 뒤에도 외부 변환이 진행돼 고아 파일이 남는다.
    @Test void 트랜잭션_안에서는_커밋된_뒤에만_worker를_트리거한다() {
        inTransaction(() -> {
            service.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                    MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.WEB);
            verifyNoInteractions(worker);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        });
        verify(worker).triggerAsync(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.WEB);
    }

    @Test void 트랜잭션이_롤백되면_worker를_트리거하지_않는다() {
        inTransaction(() -> {
            service.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                    MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        });
        verifyNoInteractions(worker);
    }

    @Test void 교체도_커밋된_뒤_한_번만_트리거한다() {
        when(assets.findByOwnerForUpdate(MediaOwnerType.JOB_POSTING, "posting-1")).thenReturn(List.of());
        inTransaction(() -> {
            service.replaceAndTriggerProcessing(MediaOwnerType.JOB_POSTING, "posting-1", List.of("raw/2.jpg"),
                    MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
            verifyNoInteractions(worker);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        });
        verify(worker, times(1)).triggerAsync(MediaOwnerType.JOB_POSTING, "posting-1", List.of("raw/2.jpg"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
    }

    // afterCommit에서 던진 예외는 이미 커밋된 요청의 호출자에게 전파된다 — 데이터는 저장됐는데 500이 나가
    // 클라이언트가 재시도하면 중복 생성된다. 배포 종료 중 @Async 제출 거부가 그 경우다.
    @Test void 커밋_뒤_트리거가_실패해도_호출자에게_전파하지_않는다() {
        doThrow(new TaskRejectedException("executor 종료 중"))
                .when(worker).triggerAsync(any(), any(), any(), any(), any());
        inTransaction(() -> {
            service.registerAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                    MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
            assertThatNoException().isThrownBy(() ->
                    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
        });
        // 예외가 난 경로를 실제로 지났는지 — 동기화가 등록되지 않도록 퇴행하면 여기서 걸린다.
        verify(worker).triggerAsync(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/1.jpg"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
    }

    /** 실제 트랜잭션 매니저 없이 동기화만 켜서 afterCommit·afterCompletion을 직접 부른다. */
    private static void inTransaction(Runnable body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
