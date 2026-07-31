package com.atcrew.recruit.internal.application;

import com.atcrew.member.ArtistProfileViewedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * member 모듈의 ArtistProfileViewedEvent를 수신해 최근 본 작가로 기록하는 리스너 단위 테스트(이슈 #37).
 */
class ArtistProfileViewListenerTest {

    LikedArtistService likedArtistService = mock(LikedArtistService.class);
    ArtistProfileViewListener listener = new ArtistProfileViewListener(likedArtistService);

    @Test
    void 조회자가_있으면_최근_본_작가로_기록한다() {
        ArtistProfileViewedEvent event = new ArtistProfileViewedEvent("company-001", "artist-001", Instant.now());

        listener.onArtistProfileViewed(event);

        verify(likedArtistService).recordArtistView("company-001", "artist-001");
    }

    @Test
    void 비로그인_조회는_무시한다() {
        ArtistProfileViewedEvent event = new ArtistProfileViewedEvent(null, "artist-001", Instant.now());

        listener.onArtistProfileViewed(event);

        verify(likedArtistService, never()).recordArtistView(any(), any());
    }

    @Test
    void 기록_중_예외가_발생해도_전파하지_않는다() {
        doThrow(new RuntimeException("db down"))
                .when(likedArtistService).recordArtistView("company-002", "artist-002");
        ArtistProfileViewedEvent event = new ArtistProfileViewedEvent("company-002", "artist-002", Instant.now());

        listener.onArtistProfileViewed(event);

        verify(likedArtistService).recordArtistView("company-002", "artist-002");
    }
}
