package com.atcrew.search.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SearchController 에러 응답 restdocs-api-spec 문서화.
 *
 * <p>{@code SearchErrorCode}의 4xx 코드(INVALID_SIZE, INVALID_CURSOR)를 실제로 발생시켜
 * REST Docs 스니펫과 openapi3 예시를 생성한다. 두 API 모두 인증이 필요 없다.
 */
class SearchControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void size가_1_미만이면_400_INVALID_SIZE() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "고양이").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
                .andDo(document("search/search-invalid-size",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("size가 1 미만인 검색 요청 — 400 INVALID_SIZE")));
    }

    @Test
    void 커서_형식이_잘못되면_400_INVALID_CURSOR() throws Exception {
        // postTypes 미지정 상태에서 q가 있으면 병합검색 경로를 타 커서 디코딩이 ES 조회보다 먼저 일어난다
        // (SearchServiceImpl.searchMerged -> MergedSearchCursor.decode).
        mockMvc.perform(get("/api/search").param("q", "고양이").param("cursor", "!!!not-base64!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andDo(document("search/search-invalid-cursor",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("형식이 잘못된 커서로 검색 요청 — 400 INVALID_CURSOR")));
    }
}
