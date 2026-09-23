package com.atcrew.media.internal.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atcrew.media.MediaAssetProcessedEvent;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaService;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MediaCallbackServiceTest {

    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final MediaCallbackService service = new MediaCallbackService(assets, events, mediaService);

    // 이미지 교체·영구 삭제 뒤 늦게 온 콜백 — Worker가 이미 써 둔 변형본은 어디서도 참조하지 않으므로
    // 고아 정리 대상에 넣어야 R2에 쌓이지 않는다. 원본은 교체·삭제 경로가 이미 넘겼으므로 넣지 않는다.
    @Test
    void 대상_자산이_없는_콜백의_변형본은_고아_정리_대상에_넣는다() {
        when(assets.findByOwnerAndOriginalKeyForUpdate(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png"))
                .thenReturn(Optional.empty());

        service.process(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png", "thumb/1.avif", null,
                "original/1.avif", MediaProcessingStatus.DONE);

        // null·빈 key 거르기는 markOrphaned 한 곳이 맡는다.
        verify(mediaService).markOrphaned(Arrays.asList("thumb/1.avif", null, "original/1.avif"));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void 대상_자산이_있으면_고아_큐를_건드리지_않고_이벤트를_발행한다() {
        when(assets.findByOwnerAndOriginalKeyForUpdate(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png"))
                .thenReturn(Optional.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/1.png",
                        MediaVariantProfile.ORIGINAL, MediaQualityTier.WEB)));

        service.process(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png", "thumb/1.avif", null,
                "original/1.avif", MediaProcessingStatus.DONE);

        verify(mediaService, never()).markOrphaned(any());
        verify(events).publishEvent(any(MediaAssetProcessedEvent.class));
    }
}
