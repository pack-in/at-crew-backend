package com.atcrew.artwork.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 북마크 폴더 이름 변경 API(PATCH /api/bookmarks/folders/{folderId}) 문서화 통합 테스트 (이슈 #80).
 *
 * <p>전체 Spring 컨텍스트(MariaDB Testcontainer)를 기동해 성공·검증 실패·소유자 불일치·존재하지
 * 않는 폴더 시나리오를 REST Docs 스니펫으로 생성한다({@code CompanyApiDocTest}와 동일한 패턴).
 */
class BookmarkApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 북마크_폴더_이름_변경_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("폴더이름변경유저");
        String folderId = createFolder(accessToken, "변경전폴더");

        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", folderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "변경후폴더"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.name").value("변경후폴더"))
                .andDo(document("bookmark/rename-folder",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("folderId").description("폴더 ID")
                        ),
                        requestFields(
                                fieldWithPath("name").description("변경할 폴더명 (공백 불가, 최대 20자)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("폴더 ID"),
                                fieldWithPath("data.name").description("변경된 폴더명"),
                                fieldWithPath("data.sortOrder").description("정렬 순서"),
                                fieldWithPath("data.createdAt").description("생성 일시 (ISO 8601)")
                        )
                ));
    }

    @Test
    void 북마크_폴더_이름_변경_자기_자신과_같은_이름은_허용된다() throws Exception {
        String accessToken = registerMemberAndGetToken("동일이름유저");
        String folderId = createFolder(accessToken, "즐겨찾기");

        // 중복 검사는 자기 자신을 제외해야 한다 — 같은 이름으로 재요청해도 충돌이 나면 안 된다.
        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", folderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "즐겨찾기"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("즐겨찾기"));
    }

    @Test
    void 북마크_폴더_이름_변경_다른_폴더와_이름이_겹치면_409() throws Exception {
        String accessToken = registerMemberAndGetToken("중복이름유저");
        createFolder(accessToken, "먼저생성된폴더");
        String folderId = createFolder(accessToken, "나중생성된폴더");

        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", folderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "먼저생성된폴더"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_FOLDER_DUPLICATE_NAME"))
                .andDo(document("bookmark/rename-folder-duplicate", preprocessResponse(prettyPrint())));
    }

    @Test
    void 북마크_폴더_이름_변경_타인_폴더는_404() throws Exception {
        String ownerToken = registerMemberAndGetToken("폴더소유자");
        String folderId = createFolder(ownerToken, "타인폴더");
        String otherToken = registerMemberAndGetToken("다른회원");

        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", folderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "가로채기"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_FOLDER_NOT_FOUND"))
                .andDo(document("bookmark/rename-folder-forbidden", preprocessResponse(prettyPrint())));
    }

    @Test
    void 북마크_폴더_이름_변경_존재하지_않는_폴더는_404() throws Exception {
        String accessToken = registerMemberAndGetToken("존재안함유저");

        mockMvc.perform(patch("/api/bookmarks/folders/{folderId}", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새이름"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_FOLDER_NOT_FOUND"));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "bookmark-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 북마크 폴더를 생성하고 folderId를 반환한다. */
    private String createFolder(String accessToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookmarks/folders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    /** 이메일 회원가입 요청 바디 (accessToken 발급용) */
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
            String countryCode,
            String primaryLanguage
    ) {}
}
