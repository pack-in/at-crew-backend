package com.atcrew.media.internal.web;

import com.atcrew.media.MediaAssetProcessedEvent;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaVariantProfile;
import com.atcrew.media.internal.application.MediaCallbackService;
import com.atcrew.media.internal.domain.MediaAsset;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구 경로 shim 검증 — Worker가 아직 {@code artworkId} 형식으로 콜백해도 ownerType=ARTWORK로 변환돼
 * 동일한 {@link MediaAssetProcessedEvent}가 발행되어야 한다(docs/design/media-module-design.md §9.2).
 */
class LegacyArtworkCallbackControllerTest {

    private MockMvc mockMvc;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(assets.findByOwnerTypeAndOwnerIdAndOriginalKey(eq(MediaOwnerType.ARTWORK), eq("artwork-1"), eq("raw/a.jpg")))
                .thenReturn(Optional.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/a.jpg",
                        MediaVariantProfile.STANDARD_WITH_ADULT_BLUR, MediaQualityTier.ORIGINAL)));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LegacyArtworkCallbackController(new MediaCallbackService(assets, events), "secret")).build();
    }

    @Test
    void 구_형식_콜백은_ownerType_ARTWORK로_변환돼_이벤트를_발행한다() throws Exception {
        mockMvc.perform(post("/internal/artwork/images/processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"artworkId":"artwork-1","imageKey":"raw/a.jpg","thumbKey":"thumb/a.avif",
                             "thumbAdultKey":"thumb-adult/a.avif","originalAvifKey":"original/a.avif","status":"DONE"}
                            """))
                .andExpect(status().isNoContent());

        verify(events).publishEvent(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1", "raw/a.jpg",
                "thumb/a.avif", "thumb-adult/a.avif", "original/a.avif", MediaProcessingStatus.DONE));
    }

    @Test
    void 잘못된_시크릿은_401로_거부된다() throws Exception {
        mockMvc.perform(post("/internal/artwork/images/processed")
                        .header("X-Internal-Secret", "wrong").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"artworkId":"artwork-1","imageKey":"raw/a.jpg","status":"DONE"}
                            """))
                .andExpect(status().isUnauthorized());

        verify(events, never()).publishEvent(any());
    }
}
