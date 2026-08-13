package com.atcrew.recruit.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구직글(JobSeekingPost) API 에러 응답 문서화 테스트.
 *
 * <p>{@code MockMvcRestDocumentationWrapper.document()} + {@code ResourceDocumentation.resource()}
 * 조합을 사용해 openapi3 태스크가 읽을 수 있는 resource.json을 생성한다(restdocs-api-spec 파이프라인).
 * JOB_SEEKING_POST_NOT_FOUND는 이 컨트롤러 경로가 가장 간단한 대표 트리거다.
 */
class JobSeekingPostErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 존재하지_않는_구직글_조회시_404_JOB_SEEKING_POST_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/recruit/job-seeking-posts/{jobSeekingPostId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_SEEKING_POST_NOT_FOUND"))
                .andDo(document("recruit/get-job-seeking-post-not-found",
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 구직글 ID로 상세 조회 — 404 JOB_SEEKING_POST_NOT_FOUND")));
    }

    // INVALID_CURSOR는 이 컨트롤러(getPublished)가 커서를 raw ID로 취급해 형식 검증이 없어
    // 재현 불가 — JobPostingErrorApiSpecTest에서 CompositeCursor.decode를 쓰는
    // GET /api/recruit/job-postings로 대표 커버한다.
}
