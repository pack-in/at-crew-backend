package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaAssetProcessedEvent;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.member.Language;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** media 처리 결과 → artwork 로컬 읽기 모델 반영 리스너(docs/design/media-module-design.md §5). */
class ArtworkMediaEventListenerTest {

    private final ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ArtworkMediaEventListener listener =
            new ArtworkMediaEventListener(artworkRepository, eventPublisher, mediaService);

    @Test
    void ARTWORK_이벤트를_받으면_이미지_처리결과를_반영하고_변경이벤트를_발행한다() {
        Artwork artwork = artworkWith("raw/1.png");
        when(artworkRepository.findByIdForUpdate("artwork-1")).thenReturn(Optional.of(artwork));
        // 변환 결과는 media가 이미 저장했다 — 리스너는 현황을 읽어 작품 상태만 정한다(#193).
        givenAssets("artwork-1", MediaProcessingStatus.DONE);

        listener.onMediaAssetProcessed(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1",
                "raw/1.png", "thumb/1.avif", "thumb-adult/1.avif", "original/1.avif", MediaProcessingStatus.DONE));

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.READY);
        verify(artworkRepository).save(artwork);
        verify(eventPublisher).publishEvent(new ArtworkChangedEvent(artwork.getId()));
    }

    @Test
    void 아직_처리중인_이미지가_있으면_PROCESSING을_유지한다() {
        Artwork artwork = artworkWith("raw/1.png", "raw/2.png");
        when(artworkRepository.findByIdForUpdate("artwork-1")).thenReturn(Optional.of(artwork));
        givenAssets("artwork-1", MediaProcessingStatus.FAILED, MediaProcessingStatus.PENDING);

        listener.onMediaAssetProcessed(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1",
                "raw/1.png", null, null, null, MediaProcessingStatus.FAILED));

        assertThat(artwork.getStatus()).isEqualTo(ArtworkStatus.PROCESSING);
    }

    @Test
    void 다른_소유자_타입의_이벤트는_무시한다() {
        listener.onMediaAssetProcessed(new MediaAssetProcessedEvent(MediaOwnerType.JOB_POSTING, "posting-1",
                "raw/1.png", "thumb/1.avif", null, "original/1.avif", MediaProcessingStatus.DONE));

        verify(artworkRepository, never()).findByIdForUpdate(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 존재하지_않는_작품의_이벤트는_무시한다() {
        when(artworkRepository.findByIdForUpdate("사라진작품")).thenReturn(Optional.empty());

        listener.onMediaAssetProcessed(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "사라진작품",
                "raw/1.png", "thumb/1.avif", null, "original/1.avif", MediaProcessingStatus.DONE));

        verify(artworkRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private void givenAssets(String artworkId, MediaProcessingStatus... statuses) {
        List<MediaAssetInfo> assets = new java.util.ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            assets.add(new MediaAssetInfo("raw/" + (i + 1) + ".png", "thumb/" + (i + 1) + ".avif", null,
                    "original/" + (i + 1) + ".avif", statuses[i], i, null));
        }
        when(mediaService.getAssets(MediaOwnerType.ARTWORK, artworkId)).thenReturn(assets);
    }

    private Artwork artworkWith(String... imageKeys) {
        return Artwork.create("author-1", "제목", "설명", List.of(imageKeys), 0, null,
                ImageLayoutType.VERTICAL_SCROLL, ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(), AgeRating.ALL, List.of(Language.KO), Visibility.PUBLIC,
                List.of(), null, null, List.of(), List.of());
    }
}
