package com.atcrew.community.internal.web;

import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.community.BannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BannerController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class BannerControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    BannerService bannerService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        bannerService = mock(BannerService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BannerController(bannerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    @Test
    void 배너_등록_memberId_blank_400() throws Exception {
        mockMvc.perform(post("/api/community/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", "", "imageUrl", "https://img.example/a.png", "linkUrl", "https://example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("community/validation/create-banner-blank-member-id"));
    }

    @Test
    void 배너_등록_imageUrl_blank_400() throws Exception {
        mockMvc.perform(post("/api/community/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", "member-1", "imageUrl", "", "linkUrl", "https://example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("community/validation/create-banner-blank-image-url"));
    }

    @Test
    void 배너_등록_linkUrl_blank_400() throws Exception {
        mockMvc.perform(post("/api/community/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", "member-1", "imageUrl", "https://img.example/a.png", "linkUrl", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("community/validation/create-banner-blank-link-url"));
    }

    @Test
    void 배너_등록_imageUrl_500자_초과_400() throws Exception {
        String tooLong = "https://img.example/" + "a".repeat(500);
        mockMvc.perform(post("/api/community/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", "member-1", "imageUrl", tooLong, "linkUrl", "https://example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("community/validation/create-banner-image-url-too-long"));
    }

    @Test
    void 배너_등록_sortOrder_음수_400() throws Exception {
        mockMvc.perform(post("/api/community/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "memberId", "member-1", "imageUrl", "https://img.example/a.png",
                                "linkUrl", "https://example.com", "sortOrder", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("community/validation/create-banner-negative-sort-order"));
    }
}
