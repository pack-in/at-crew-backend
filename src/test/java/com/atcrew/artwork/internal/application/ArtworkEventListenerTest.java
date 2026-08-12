package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import com.atcrew.media.RetainedMediaKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 영구 삭제 시 R2 파일 제거를 media에 위임하는지 검증한다
 * (docs/design/media-module-design.md §4 — QA에서 발견한 누락 소비자).
 */
class ArtworkEventListenerTest {

    private final ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RetainedMediaKeyProvider retainedMediaKeyProvider = mock(RetainedMediaKeyProvider.class);
    private final ArtworkEventListener listener =
            new ArtworkEventListener(artworkRepository, mediaService, eventPublisher,
                    List.of(retainedMediaKeyProvider));

    @Test
    void 영구삭제_이벤트는_media에_파일_삭제를_위임한다() {
        List<String> keys = List.of("raw/1.png", "thumb/1.avif");

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService).deleteFiles(keys);
        verify(mediaService, never()).markOrphaned(anyList());
        verify(mediaService).deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");
    }

    @Test
    void 파일_삭제에_실패하면_고아_키로_적재한다() {
        List<String> keys = List.of("raw/1.png");
        doThrow(new IllegalStateException("R2 삭제 실패")).when(mediaService).deleteFiles(keys);

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService).markOrphaned(keys);
    }

    @Test
    void R2_삭제_실패해도_media_assets_행은_정리한다() {
        List<String> keys = List.of("raw/1.png");
        doThrow(new IllegalStateException("R2 삭제 실패")).when(mediaService).deleteFiles(keys);

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService).deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");
    }

    @Test
    void 활성_고정형_스냅샷이_참조_중인_키는_삭제하지_않는다() {
        List<String> keys = List.of("raw/1.png", "thumb/1.avif", "raw/2.png");
        when(retainedMediaKeyProvider.retainedKeys(anyCollection())).thenReturn(Set.of("thumb/1.avif"));

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService).deleteFiles(List.of("raw/1.png", "raw/2.png"));
    }

    @Test
    void 삭제할_키가_전부_보존_대상이면_삭제_요청도_비어있다() {
        List<String> keys = List.of("raw/1.png");
        when(retainedMediaKeyProvider.retainedKeys(anyCollection())).thenReturn(Set.of("raw/1.png"));

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService).deleteFiles(List.of());
        verify(mediaService, never()).markOrphaned(anyList());
    }

    @Test
    void 보존_판정이_실패하면_전체_키를_고아_큐로_넘긴다() {
        List<String> keys = List.of("raw/1.png");
        when(retainedMediaKeyProvider.retainedKeys(anyCollection()))
                .thenThrow(new IllegalStateException("스냅샷 조회 실패"));

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", keys));

        verify(mediaService, never()).deleteFiles(anyList());
        verify(mediaService).markOrphaned(keys);
    }

    @Test
    void 삭제할_키가_없으면_고아_적재도_하지_않는다() {
        doThrow(new IllegalStateException("R2 삭제 실패")).when(mediaService).deleteFiles(List.of());

        listener.onPermanentlyDeleted(new ArtworkPermanentlyDeletedEvent("artwork-1", List.of()));

        verify(mediaService, never()).markOrphaned(anyList());
        verify(mediaService).deleteAssetsForOwner(MediaOwnerType.ARTWORK, "artwork-1");
    }
}
