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
    private MediaService mediaService;
    private MediaAsset thumbnailAsset;

    @BeforeEach void setUp() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        events = mock(ApplicationEventPublisher.class);
        when(assets.findByOwnerAndOriginalKeyForUpdate(eq(MediaOwnerType.ARTWORK), eq("artwork-1"), eq("raw/a.jpg")))
                .thenReturn(Optional.of(MediaAsset.pending(MediaOwnerType.ARTWORK, "artwork-1", 0, "raw/a.jpg", MediaVariantProfile.ORIGINAL, MediaQualityTier.ORIGINAL)));
        thumbnailAsset = MediaAsset.pending(MediaOwnerType.ARTWORK_THUMBNAIL, "artwork-1", 0, "raw/t.png",
                MediaVariantProfile.THUMBNAIL_WITH_ADULT_BLUR, MediaQualityTier.WEB);
        thumbnailAsset.markProcessed("thumb/t.avif", "thumb-adult/t.avif", null, MediaProcessingStatus.DONE);
        when(assets.findByOwnerAndOriginalKeyForUpdate(eq(MediaOwnerType.ARTWORK_THUMBNAIL), eq("artwork-1"), eq("raw/t.png")))
                .thenReturn(Optional.of(thumbnailAsset));
        mediaService = mock(MediaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaInternalController(new MediaCallbackService(assets, events, mediaService), mock(com.atcrew.media.internal.application.OgBackfillService.class), "secret")).build();
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

    @Test void webhookStoresOgKeys() throws Exception {
        mockMvc.perform(post("/internal/media/images/processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK_THUMBNAIL","ownerId":"artwork-1","imageKey":"raw/t.png",
                             "thumbKey":"thumb/t.avif","thumbAdultKey":"thumb-adult/t.avif","status":"DONE",
                             "ogKey":"og/t.jpg","ogAdultKey":"og-adult/t.jpg"}
                            """))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgKey()).isEqualTo("og/t.jpg");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgAdultKey()).isEqualTo("og-adult/t.jpg");
    }

    // OG 전용 콜백(백필)은 썸네일 필드를 싣지 않는다 — 썸네일 key와 처리 상태가 그대로 남아야 카드가 깨지지 않는다.
    @Test void ogCallbackAttachesOgKeysWithoutTouchingThumbnail() throws Exception {
        mockMvc.perform(post("/internal/media/images/og-processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK_THUMBNAIL","ownerId":"artwork-1","imageKey":"raw/t.png",
                             "ogKey":"og/t.jpg","ogAdultKey":"og-adult/t.jpg"}
                            """))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgKey()).isEqualTo("og/t.jpg");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgAdultKey()).isEqualTo("og-adult/t.jpg");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getThumbKey()).isEqualTo("thumb/t.avif");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getThumbAdultKey()).isEqualTo("thumb-adult/t.avif");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getProcessingStatus()).isEqualTo(MediaProcessingStatus.DONE);
        verify(events, never()).publishEvent(any());
    }

    // 백필을 다시 돌렸는데 이번엔 다른 쪽이 실패한 경우 — 이미 기록된 key를 null로 지우면 그 파일이 추적에서 빠진다.
    @Test void ogCallbackKeepsExistingKeyWhenOneSideFailsAgain() throws Exception {
        thumbnailAsset.attachOgKeys(null, "og-adult/t.jpg");
        mockMvc.perform(post("/internal/media/images/og-processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK_THUMBNAIL","ownerId":"artwork-1","imageKey":"raw/t.png",
                             "ogKey":"og/t.jpg","ogAdultKey":null}
                            """))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgKey()).isEqualTo("og/t.jpg");
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgAdultKey()).isEqualTo("og-adult/t.jpg");
    }

    // 백필 도중 썸네일이 교체·삭제되면 행이 없다 — 이미 R2에 쓴 og 파일은 고아 정리로 보낸다.
    @Test void ogCallbackForMissingAssetOrphansOgFiles() throws Exception {
        mockMvc.perform(post("/internal/media/images/og-processed")
                        .header("X-Internal-Secret", "secret").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK_THUMBNAIL","ownerId":"artwork-1","imageKey":"raw/gone.png",
                             "ogKey":"og/gone.jpg","ogAdultKey":null}
                            """))
                .andExpect(status().isNoContent());
        verify(mediaService).markOrphaned(java.util.Arrays.asList("og/gone.jpg", null));
    }

    @Test void ogCallbackRejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/internal/media/images/og-processed")
                        .header("X-Internal-Secret", "wrong").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK_THUMBNAIL","ownerId":"artwork-1","imageKey":"raw/t.png","ogKey":"og/t.jpg"}
                            """))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(thumbnailAsset.getOgKey()).isNull();
    }
}
