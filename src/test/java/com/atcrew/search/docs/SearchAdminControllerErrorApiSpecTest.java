package com.atcrew.search.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchAdminController 에러 응답 restdocs-api-spec 문서화.
 *
 * <p>{@code SearchErrorCode.INTERNAL_SECRET_INVALID}(401)를 X-Internal-Secret 헤더 값이
 * 틀린 요청으로 실제로 발생시켜 REST Docs 스니펫과 openapi3 예시를 생성한다.
 * 테스트 프로필의 실제 시크릿 값은 src/test/resources/application.yml의
 * {@code search.internal.secret: test-internal-secret}이다.
 */
class SearchAdminControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 내부_시크릿이_틀리면_401_INTERNAL_SECRET_INVALID() throws Exception {
        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SECRET_INVALID"))
                .andDo(document("search/reindex-invalid-secret",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("내부 시크릿이 일치하지 않는 재색인 요청 — 401 INTERNAL_SECRET_INVALID")));
    }
}
