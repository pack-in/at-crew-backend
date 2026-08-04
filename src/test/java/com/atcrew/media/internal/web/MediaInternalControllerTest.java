package com.atcrew.media.internal.web;

import com.atcrew.media.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaInternalControllerTest {
    private MockMvc mockMvc;
    private ApplicationEventPublisher events;

    @BeforeEach void setUp() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(assets.findByOwnerTypeAndOwnerIdAndOriginalKey(eq(MediaOwnerType.ARTWORK), eq("artwork-1"), eq("raw/a.jpg")))
                .thenReturn(Optional.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/a.jpg", MediaVariantProfile.STANDARD)));
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaInternalController(new MediaCallbackService(assets, events), "secret")).build();
    }

    @Test void webhookPublishesEventAndIgnoresUnknownJsonFields() throws Exception {
        mockMvc.perform(post("/internal/media/images/processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK","ownerId":"artwork-1","imageKey":"raw/a.jpg",
                             "thumbKey":"thumb/a.avif","originalAvifKey":"original/a.avif","status":"DONE",
                             "futureWorkerField":"ignored"}
                            """))
                .andExpect(status().isNoContent());
        verify(events).publishEvent(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1", "raw/a.jpg",
                "thumb/a.avif", null, "original/a.avif", MediaProcessingStatus.DONE));
    }
}
