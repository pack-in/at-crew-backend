package com.atcrew.artwork.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TrashController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>{@code ArtworkErrorCode.ARTWORK_NOT_DELETED}를 대표로 커버한다 — ARTWORK_NOT_FOUND·
 * ARTWORK_ACCESS_DENIED는 이 컨트롤러의 복구·영구삭제 API에서도 동일하게 던져지지만 이미
 * {@code ArtworkControllerErrorApiSpecTest}에서 대표 케이스를 문서화했으므로 중복 작성하지 않는다.
 */
class TrashControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 휴지통에_없는_작품_복구시_400_ARTWORK_NOT_DELETED() throws Exception {
        RegisteredMember member = registerMember("휴지통아닌작품주인");
        String artworkId = uploadArtwork(member.accessToken());

        Map<String, Object> body = Map.of("artworkIds", List.of(artworkId));

        mockMvc.perform(post("/api/trash/artworks/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTWORK_NOT_DELETED"))
                .andDo(document("trash/restore-not-deleted",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("휴지통에 있지 않은 작품을 복구 시도 — 400 ARTWORK_NOT_DELETED")));
    }

    private String uploadArtwork(String accessToken) throws Exception {
        Map<String, Object> uploadBody = new LinkedHashMap<>();
        uploadBody.put("imageKeys", List.of("raw/" + UUID.randomUUID() + ".png"));
        uploadBody.put("representativeImageIndex", 0);
        uploadBody.put("imageLayoutType", "VERTICAL_SCROLL");
        uploadBody.put("title", "휴지통용 작품");
        uploadBody.put("artworkField", "ILLUSTRATION");
        uploadBody.put("creativeType", "ORIGINAL");
        uploadBody.put("roles", List.of("LINEART"));
        uploadBody.put("ageRating", "ALL");
        uploadBody.put("visibility", "PUBLIC");

        MvcResult result = mockMvc.perform(post("/api/artworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadBody)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "trash-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(
                objectMapper.readTree(body).at("/data/accessToken").asText(),
                objectMapper.readTree(body).at("/data/member/id").asText());
    }

    private record RegisteredMember(String accessToken, String memberId) {
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
