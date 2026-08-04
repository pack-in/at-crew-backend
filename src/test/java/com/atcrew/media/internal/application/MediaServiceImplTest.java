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
    private final MediaService service = new MediaServiceImpl(assets, mock(OrphanedMediaKeyRepository.class), storage, mock(ImageProcessingWorker.class));

    @Test void deleteAssetsForOwnerRemovesAllMatchingRows() {
        var existing = List.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.jpg", MediaVariantProfile.STANDARD_WITH_ADULT_BLUR));
        when(assets.findByOwnerTypeAndOwnerIdOrderByOrdinalAsc(MediaOwnerType.ARTWORK, "artwork-1")).thenReturn(existing);

        service.deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");

        verify(assets).deleteAll(existing);
    }

    @Test void presignAcceptsOneToTwentySupportedImageTypes() {
        when(storage.generatePresignedPutUrl(anyString(), eq("image/jpeg"))).thenReturn("https://upload.example");
        var urls = service.generatePresignedUrls(1, List.of("image/jpeg"));
        assertThat(urls).hasSize(1);
        assertThat(urls.getFirst().key()).startsWith("raw/").endsWith(".jpg");
        assertThat(urls.getFirst().uploadUrl()).isEqualTo("https://upload.example");
    }

    @Test void presignRejectsCountsOutsideOneToTwentyAndUnsupportedContentTypes() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(21, java.util.Collections.nCopies(21, "image/jpeg")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePresignedUrls(1, List.of("image/gif")));
    }
}
