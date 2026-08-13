package com.atcrew.community.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BannerController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>CommunityErrorCode 중 BANNER_NOT_FOUND를 대표로 커버한다 — 존재하지 않는 배너 ID로
 * 삭제를 시도해 404 응답을 실제로 발생시킨다.
 */
class BannerControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 존재하지_않는_배너_삭제시_404_BANNER_NOT_FOUND() throws Exception {
        RegisteredMember member = registerMember("배너없음유저");
        String nonExistentBannerId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/community/banners/{bannerId}", nonExistentBannerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BANNER_NOT_FOUND"))
                .andDo(document("community/delete-banner-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 배너 ID로 삭제 시도 — 404 BANNER_NOT_FOUND")));
    }

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "banner-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(objectMapper.readTree(body).at("/data/accessToken").asText());
    }

    private record RegisteredMember(String accessToken) {
    }

    record RegisterRequest(
            String email,
            String password,
            String passwordConfirm,
            String name,
            boolean agreeService,
            boolean agreePrivacy,
            boolean agreeThirdParty,
            boolean agreeMarketing,
            String timezone,
            String countryCode
    ) {}
}
