package com.atcrew.media.internal.web;

import com.atcrew.media.*;
import com.atcrew.media.MediaQualityTier;
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
                .thenReturn(Optional.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/a.jpg", MediaVariantProfile.STANDARD, MediaQualityTier.ORIGINAL)));
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

    // 실패 사유는 저장하지 않고 로그로만 남기므로 여기서는 "사유가 와도 처리가 깨지지 않는다"까지만 본다.
    // Worker가 보내는 payload 계약이 서버와 맞는지 확인하는 것이 목적이다.
    @Test void webhookAcceptsFailureReasonOnFailedCallback() throws Exception {
        mockMvc.perform(post("/internal/media/images/processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK","ownerId":"artwork-1","imageKey":"raw/a.jpg",
                             "thumbKey":null,"thumbAdultKey":null,"originalAvifKey":null,"status":"FAILED",
                             "failureReason":"변환 실패: status=409 면적 초과"}
                            """))
                .andExpect(status().isNoContent());
        verify(events).publishEvent(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1", "raw/a.jpg",
                null, null, null, MediaProcessingStatus.FAILED));
    }

    // 구버전 Worker는 failureReason을 아예 보내지 않는다 — 그때도 그대로 동작해야 배포 순서에 묶이지 않는다.
    @Test void webhookAcceptsFailedCallbackWithoutFailureReason() throws Exception {
        mockMvc.perform(post("/internal/media/images/processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK","ownerId":"artwork-1","imageKey":"raw/a.jpg",
                             "status":"FAILED"}
                            """))
                .andExpect(status().isNoContent());
        verify(events).publishEvent(new MediaAssetProcessedEvent(MediaOwnerType.ARTWORK, "artwork-1", "raw/a.jpg",
                null, null, null, MediaProcessingStatus.FAILED));
    }
}
