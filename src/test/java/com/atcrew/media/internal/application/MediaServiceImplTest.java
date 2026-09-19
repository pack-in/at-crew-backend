package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
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

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");

        verify(assets).deleteAll(existing);
        // 지운 행이 가리키던 파일은 고아 큐로 간다 — 영구 삭제 이벤트 뒤에 도착한 콜백이 남긴 변형본을 놓치지 않는다.
        var orphaned = org.mockito.ArgumentCaptor.forClass(com.atcrew.media.internal.domain.OrphanedMediaKey.class);
        verify(orphans).save(orphaned.capture());
        assertThat(orphaned.getValue().getKeys()).containsExactly("raw/1.jpg");
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
        doThrow(new org.springframework.core.task.TaskRejectedException("executor 종료 중"))
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

    // 부분 교체 — 남는 key는 처리 결과를 넘겨받고 다시 트리거하지 않는다. Worker는 변환을 마치면 raw를 지우므로
    // 재변환은 FAILED가 되고, 남는 key를 고아로 넘기면 정리 배치가 남긴 이미지 파일을 지운다.
    @Test void 부분_교체는_빠진_키만_고아로_넘기고_새_키만_트리거한다() {
        MediaAsset kept1 = done("raw/1.jpg", 0), removed = done("raw/2.jpg", 1), kept3 = done("raw/3.jpg", 2);
        when(assets.findByOwnerForUpdate(MediaOwnerType.ARTWORK, "artwork-1"))
                .thenReturn(List.of(kept1, removed, kept3));

        service.replaceAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/3.jpg", "raw/1.jpg", "raw/9.jpg"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);

        var orphaned = org.mockito.ArgumentCaptor.forClass(com.atcrew.media.internal.domain.OrphanedMediaKey.class);
        verify(orphans).save(orphaned.capture());
        assertThat(orphaned.getValue().getKeys())
                .containsExactlyInAnyOrder("raw/2.jpg", "thumb/2.avif", "original/2.avif");
        var saved = org.mockito.ArgumentCaptor.forClass(MediaAsset.class);
        verify(assets, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(MediaAsset::getOriginalKey, MediaAsset::getOrdinal, MediaAsset::getThumbKey)
                .containsExactly(tuple("raw/3.jpg", 0, "thumb/3.avif"), tuple("raw/1.jpg", 1, "thumb/1.avif"),
                        tuple("raw/9.jpg", 2, null));
        verify(worker).triggerAsync(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/9.jpg"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
    }

    @Test void 순서만_바꾸면_트리거도_고아_처리도_없다() {
        when(assets.findByOwnerForUpdate(MediaOwnerType.ARTWORK, "artwork-1"))
                .thenReturn(List.of(done("raw/1.jpg", 0), done("raw/2.jpg", 1)));

        service.replaceAndTriggerProcessing(MediaOwnerType.ARTWORK, "artwork-1", List.of("raw/2.jpg", "raw/1.jpg"),
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);

        verifyNoInteractions(worker);
        verify(orphans, never()).save(any());
    }

    private static MediaAsset done(String rawKey, int ordinal) {
        String name = rawKey.substring(4, rawKey.lastIndexOf('.'));
        MediaAsset asset = MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", ordinal, rawKey,
                MediaVariantProfile.STANDARD, MediaQualityTier.WEB);
        asset.markProcessed("thumb/" + name + ".avif", null, "original/" + name + ".avif", MediaProcessingStatus.DONE);
        return asset;
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
