package com.atcrew.media.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.domain.OrphanedMediaKey;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import com.atcrew.media.internal.persistence.OrphanedMediaKeyRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MediaCallbackServiceTest {

    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OrphanedMediaKeyRepository orphans = mock(OrphanedMediaKeyRepository.class);
    private final MediaCallbackService service = new MediaCallbackService(assets, events, orphans);

    // 이미지 교체·영구 삭제 뒤 늦게 온 콜백 — Worker가 이미 써 둔 변형본은 어디서도 참조하지 않으므로
    // 고아 정리 대상에 넣어야 R2에 쌓이지 않는다. 원본은 교체·삭제 경로가 이미 넘겼으므로 넣지 않는다.
    @Test
    void 대상_자산이_없는_콜백의_변형본은_고아_정리_대상에_넣는다() {
        when(assets.findByOwnerTypeAndOwnerIdAndOriginalKey(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png"))
                .thenReturn(Optional.empty());

        service.process(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png", "thumb/1.avif", null,
                "original/1.avif", MediaProcessingStatus.DONE);

        ArgumentCaptor<OrphanedMediaKey> saved = ArgumentCaptor.forClass(OrphanedMediaKey.class);
        verify(orphans).save(saved.capture());
        assertThat(saved.getValue().getKeys()).containsExactlyInAnyOrder("thumb/1.avif", "original/1.avif");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void 변형본이_없는_실패_콜백은_고아_큐에_아무것도_넣지_않는다() {
        when(assets.findByOwnerTypeAndOwnerIdAndOriginalKey(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png"))
                .thenReturn(Optional.empty());

        service.process(MediaOwnerType.ARTWORK, "artwork-1", "raw/1.png", null, null, null,
                MediaProcessingStatus.FAILED, "원본 없음");

        verify(orphans, never()).save(any());
    }
}
