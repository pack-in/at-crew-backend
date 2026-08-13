package com.atcrew.member.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
 * DevMemberController(개발·테스트 전용 가입 API)가 던지는 MemberErrorCode 에러 응답을 실제로
 * 재현해 REST Docs 스니펫과 openapi3 예시를 생성한다 (restdocs-api-spec 파이프라인).
 *
 * <p>이 컨트롤러는 {@code @Hidden}이라 Swagger UI에는 노출되지 않지만, 응답 바디의 code 값이
 * 실제 MemberErrorCode.message와 일치하는지는 여전히 검증할 가치가 있어 문서화 테스트를 둔다.
 */
class DevMemberControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 이미_사용중인_핸들로_가입시_409_DUPLICATE_HANDLE() throws Exception {
        String sharedHandle = "dup_handle_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 선점: 해당 핸들로 먼저 가입
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody(
                                "dev-dup-handle-1-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                                sharedHandle))))
                .andExpect(status().isCreated());

        // 같은 핸들로 재시도
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody(
                                "dev-dup-handle-2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                                sharedHandle))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_HANDLE"))
                .andDo(document("member/dev-register-duplicate-handle",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미 사용 중인 핸들로 개발용 가입 API 호출 — 409 DUPLICATE_HANDLE")));
    }

    private Map<String, Object> registerBody(String email, String handle) {
        return Map.of(
                "loginEmail", email,
                "handle", handle,
                "name", "핸들중복유저",
                "creatorRole", "WEBTOON"
        );
    }
}
