package com.atcrew.recruit.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구인/구직 이미지 Presigned URL 발급 API 문서화 테스트
 * (docs/design/media-module-design.md §10.3).
 *
 * <p>게시글의 {@code thumbnailImage}/{@code referenceImages}에는 이제 임의의 URL이 아니라
 * 여기서 발급받은 {@code key}를 넣어야 media 모듈의 변환 파이프라인을 탄다(§10.4).
 */
class RecruitImageApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 이미지_presigned_url_발급_문서화() throws Exception {
        String token = registerMemberAndGetToken("이미지업로더");

        mockMvc.perform(post("/api/recruit/images/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("count", 2, "contentTypes", List.of("image/jpeg", "image/png")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].key").isNotEmpty())
                .andExpect(jsonPath("$.data[0].uploadUrl").isNotEmpty())
                .andDo(document("recruit/presign-images",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("count").description("발급할 URL 개수 (1~20)"),
                                fieldWithPath("contentTypes").description(
                                        "이미지별 content type (image/jpeg·image/png·image/webp), count와 개수가 같아야 함")),
                        relaxedResponseFields(
                                fieldWithPath("data[].key").description("업로드 후 게시글 요청에 넣을 이미지 key"),
                                fieldWithPath("data[].uploadUrl").description("R2에 PUT할 Presigned URL"))));
    }

    @Test
    void 개수_제한을_넘으면_거부된다() throws Exception {
        String token = registerMemberAndGetToken("이미지업로더거부");

        mockMvc.perform(post("/api/recruit/images/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("count", 0, "contentTypes", List.of("image/jpeg")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비로그인_발급_요청은_거부된다() throws Exception {
        mockMvc.perform(post("/api/recruit/images/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("count", 1, "contentTypes", List.of("image/jpeg")))))
                .andExpect(status().isUnauthorized());
    }

    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "recruit-media-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 이메일 회원가입 요청 바디 (accessToken 발급용) */
    record RegisterRequest(
            String email, String password, String passwordConfirm, String name,
            boolean agreeService, boolean agreePrivacy, boolean agreeThirdParty,
            boolean agreeMarketing, String timezone, String countryCode
    ) {
    }
}
