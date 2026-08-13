package com.atcrew.community.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CommunityController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>CommunityErrorCode 중 INVALID_SIZE를 대표로 커버한다 — 포트폴리오 탭(작품 목록) 조회에
 * size=0 미만 값을 전달해 400 응답을 실제로 발생시킨다. resolveSize()가 4개 탭(작품·작가·구인글·
 * 팀원모집글)에 공통 적용되므로 대표로 한 엔드포인트만 문서화한다.
 *
 * <p>기존 {@link com.atcrew.community.internal.web.CommunityControllerValidationTest}가 같은
 * 시나리오를 standaloneSetup + 일반 spring-restdocs {@code document()}로 커버하고 있었으나,
 * resource.json을 생성하지 않아 openapi3 태스크가 읽지 못한다. 이 클래스가 restdocs-api-spec
 * 형식으로 대체 문서화를 담당한다.
 */
class CommunityControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 작품_목록_size_음수_400_INVALID_SIZE() throws Exception {
        mockMvc.perform(get("/api/community/artworks").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
                .andDo(document("community/artworks-invalid-size",
                        preprocessResponse(prettyPrint()),
                        resource("size에 1 미만 값을 전달해 작품 목록 조회 시도 — 400 INVALID_SIZE")));
    }
}
