package com.atcrew.media.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * media 모듈에는 자체 ErrorCode enum이 없다 (internal/exception 패키지 자체가 없음, IllegalArgumentException·
 * ResponseStatusException만 사용). {@link com.atcrew.media.internal.web.MediaInternalController}와
 * {@link com.atcrew.media.internal.web.LegacyArtworkCallbackController}는 둘 다 {@code @Hidden} +
 * permitAll인 내부 콜백 엔드포인트로, 유일하게 실제로 분기되는 에러 조건은 {@code X-Internal-Secret}
 * 불일치 → 401이다. GlobalExceptionHandler에서 ResponseStatusException은 DomainException이 아니므로
 * 공통 코드 {@code HTTP_401}로 응답한다(docs/testing/rest-docs-guide.md §6 파이프라인 확장).
 *
 * <p>{@link com.atcrew.media.internal.web.LegacyArtworkCallbackController}도 동일한 시크릿 검증 로직을
 * 그대로 복제하고 있어(전환기 한정 shim) 같은 401 코드를 낼 뿐이므로 별도 REST Docs 테스트를 추가하지
 * 않았다 — 이미 {@code LegacyArtworkCallbackControllerTest#잘못된_시크릿은_401로_거부된다()}가
 * standalone MockMvc로 그 경로를 커버한다.
 */
class MediaInternalControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 잘못된_Internal_Secret으로_콜백_요청시_401() throws Exception {
        mockMvc.perform(post("/internal/media/images/processed")
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerType":"ARTWORK","ownerId":"artwork-1","imageKey":"raw/a.jpg","status":"DONE"}
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("HTTP_401"))
                .andDo(document("media/images-processed-invalid-secret",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("잘못된 X-Internal-Secret 헤더로 이미지 처리 콜백 시도 — 401 HTTP_401")));
    }
}
