package com.atcrew.media.internal.application;

import com.atcrew.media.*;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaServiceImplTest {
    private final ArtworkStoragePort storage = mock(ArtworkStoragePort.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final ImageProcessingWorker worker = mock(ImageProcessingWorker.class);
    private final MediaService service = new MediaServiceImpl(assets, mock(OrphanedMediaKeyRepository.class), storage, worker);

    @Test void deleteAssetsForOwnerRemovesAllMatchingRows() {
        var existing = List.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL));
        when(assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(MediaOwnerType.ARTWORK, "artwork-1")).thenReturn(existing);

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");

        verify(assets).deleteAll(existing);
    }

    @Test void presignAcceptsOneToThirtySupportedImageTypes() {
        when(storage.generatePresignedPutUrl(anyString(), eq("image/jpeg"))).thenReturn("https://upload.example");
        var urls = service.generatePresignedUrls(1, List.of("image/jpeg"));
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
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(31, java.util.Collections.nCopies(31, "image/jpeg")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(1, List.of("image/gif")));
    }
}
