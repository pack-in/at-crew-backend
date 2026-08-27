package com.atcrew.artwork.internal.web;

import com.atcrew.artwork.ArtworkService;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 노출 위치 재선언 API 계약 검증 (업로드-R09).
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동한다({@code PortfolioControllerValidationTest}와
 * 동일한 패턴). 구 {@code PATCH /visibility}(3값 공개 상태)는 제거됐으므로 더 이상 매핑되지 않아야 한다.
 */
class ArtworkPublicationControllerTest {

    private static final String ARTWORK_ID = UUID.randomUUID().toString();
    private static final String MEMBER_ID = UUID.randomUUID().toString();

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    ArtworkService artworkService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        artworkService = mock(ArtworkService.class);
        securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentMemberId()).thenReturn(MEMBER_ID);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtworkController(artworkService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 노출_위치_재선언은_피드_공개_여부와_포트폴리오_목록을_함께_전달한다() throws Exception {
        String portfolioId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("publishToFeed", false);
        body.put("portfolioIds", List.of(portfolioId));

        mockMvc.perform(patch("/api/artworks/{artworkId}/publication", ARTWORK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        verify(artworkService).updatePublication(eq(MEMBER_ID), eq(ARTWORK_ID), eq(false),
                eq(List.of(portfolioId)));
    }

    @Test
    void 피드_공개_여부_누락은_거부한다() throws Exception {
        mockMvc.perform(patch("/api/artworks/{artworkId}/publication", ARTWORK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("portfolioIds", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    // 구 계약(3값 visibility)은 즉시 전환으로 제거됐다 — 과도기 병행 없음.
    @Test
    void 구_공개상태_변경_엔드포인트는_더_이상_존재하지_않는다() throws Exception {
        mockMvc.perform(patch("/api/artworks/{artworkId}/visibility", ARTWORK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("visibility", "PUBLIC"))))
                .andExpect(status().isNotFound());
    }
}
