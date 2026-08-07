package com.atcrew.recruit.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구직글 API 문서화 통합 테스트.
 *
 * <p>전체 Spring 컨텍스트(MariaDB Testcontainer)를 기동해 구직글 작성·수정·게시·조회·상태전이·
 * 휴지통 API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 * JobSeekingPost는 관리자 승인 절차가 없으며, 별도의 publish 엔드포인트로 DRAFT/CLOSED에서
 * PUBLISHED로 전이한다(설계 §4.2).
 */
class JobSeekingPostApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 구직글_전체_생명주기_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("구직글작성자");

        // 1. 작성 — publish=false면 DRAFT로 임시저장
        MvcResult createResult = mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullCreateBody(false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andDo(document("recruit/create-job-seeking-post",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("구직글 제목 (최대 200자)"),
                                fieldWithPath("roles").description("희망 역할 (최대 20개)").optional(),
                                fieldWithPath("genres").description("희망 장르 (최대 20개)").optional(),
                                fieldWithPath("drawingStyle").description("작화 스타일 (최대 200자)").optional(),
                                fieldWithPath("preferredFeedbackStyle").description("선호 피드백 방식 (DETAILED·MINIMAL·REAL_TIME·PERIODIC)").optional(),
                                fieldWithPath("workStyle").description("작업 스타일 (INDEPENDENT·COLLABORATIVE·STRUCTURED·FLEXIBLE)").optional(),
                                fieldWithPath("desiredRate").description("희망 단가 (자유 텍스트, 최대 200자)").optional(),
                                fieldWithPath("portfolioDescription").description("포트폴리오 소개 (최대 5000자)").optional(),
                                fieldWithPath("referenceImages").description("참고 이미지 URL 목록 (최대 20개, 표시 전용)").optional(),
                                fieldWithPath("publish").description("true면 저장 직후 PUBLISHED로 게시, false면 DRAFT로 임시저장")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.authorMemberId").description("작성자(창작자) Member ID"),
                                fieldWithPath("data.title").description("구직글 제목"),
                                fieldWithPath("data.status").description("상태 (DRAFT·PUBLISHED·CLOSED·DELETED)"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601)")
                        )
                ))
                .andReturn();
        String jobSeekingPostId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 2. 수정 — 작성자 본인만 가능, 부분 업데이트(null 필드는 기존 값 유지)
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("title", "웹툰 일러스트레이터 구직 (수정)");
        updateBody.put("desiredRate", "페이지당 6만원");
        updateBody.put("portfolioDescription", "6년차 웹툰 일러스트레이터입니다");

        mockMvc.perform(put("/api/recruit/job-seeking-posts/{jobSeekingPostId}", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("웹툰 일러스트레이터 구직 (수정)"))
                .andExpect(jsonPath("$.data.desiredRate").value("페이지당 6만원"))
                .andDo(document("recruit/update-job-seeking-post",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("변경할 제목. null이면 변경 없음").optional(),
                                fieldWithPath("desiredRate").description("변경할 희망 단가. null이면 변경 없음").optional(),
                                fieldWithPath("portfolioDescription").description("변경할 포트폴리오 소개. null이면 변경 없음").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.title").description("변경된 제목"),
                                fieldWithPath("data.desiredRate").description("변경된 희망 단가")
                        )
                ));

        // 3. 게시 — DRAFT/CLOSED → PUBLISHED, 승인 절차 없이 즉시 공개
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/publish", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andDo(document("recruit/publish-job-seeking-post",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobSeekingPostId").description("구직글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.status").description("변경된 상태 (PUBLISHED)")
                        )
                ));

        // 4. 상세 조회 — 인증 불필요, PUBLISHED는 누구나 조회 가능
        mockMvc.perform(get("/api/recruit/job-seeking-posts/{jobSeekingPostId}", jobSeekingPostId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(jobSeekingPostId))
                .andDo(document("recruit/get-job-seeking-post",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobSeekingPostId").description("구직글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.authorMemberId").description("작성자(창작자) Member ID"),
                                fieldWithPath("data.authorName").description("작성자 표시명 (member 모듈 조회 실패 시 null)"),
                                fieldWithPath("data.title").description("구직글 제목"),
                                fieldWithPath("data.roles").description("희망 역할"),
                                fieldWithPath("data.genres").description("희망 장르"),
                                fieldWithPath("data.status").description("상태"),
                                fieldWithPath("data.updatedAt").description("수정 시각 (ISO 8601)")
                        )
                ));

        // 5. 목록(커서) — PUBLISHED만, 인증 불필요
        mockMvc.perform(get("/api/recruit/job-seeking-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("recruit/list-job-seeking-posts",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구직글 ID"),
                                fieldWithPath("data.items[].title").description("구직글 제목"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 6. 내 목록 — 휴지통(DELETED)을 제외한 모든 상태
        mockMvc.perform(get("/api/recruit/job-seeking-posts/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(jobSeekingPostId))
                .andDo(document("recruit/list-my-job-seeking-posts",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구직글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (DELETED 제외)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 7. 상태 변경(마감) — 현재는 CLOSED 전이만 지원
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/status", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andDo(document("recruit/update-job-seeking-post-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("status").description("변경할 상태 (현재는 CLOSED만 허용)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.status").description("변경된 상태 (CLOSED)")
                        )
                ));

        // 8. 삭제(휴지통 이동)
        mockMvc.perform(delete("/api/recruit/job-seeking-posts/{jobSeekingPostId}", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/delete-job-seeking-post",
                        pathParameters(
                                parameterWithName("jobSeekingPostId").description("구직글 ID")
                        )
                ));

        // 9. 휴지통 목록
        mockMvc.perform(get("/api/recruit/job-seeking-posts/trash")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(jobSeekingPostId))
                .andExpect(jsonPath("$.data.items[0].status").value("DELETED"))
                .andDo(document("recruit/list-trashed-job-seeking-posts",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구직글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (DELETED)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 10. 복구 — DELETED → DRAFT (승인 절차 없이도 바로 재게시하지 않고 DRAFT를 거친다)
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/restore", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andDo(document("recruit/restore-job-seeking-post",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobSeekingPostId").description("구직글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구직글 ID"),
                                fieldWithPath("data.status").description("변경된 상태 (DRAFT)")
                        )
                ));
    }

    @Test
    void 작성자가_아니면_수정할_수_없다_403_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("구직글소유자");
        String strangerToken = registerMemberAndGetToken("구직글타인");
        String jobSeekingPostId = createJobSeekingPost(authorToken);

        mockMvc.perform(put("/api/recruit/job-seeking-posts/{jobSeekingPostId}", jobSeekingPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "타인이 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_AUTHOR"))
                .andDo(document("recruit/update-job-seeking-post-forbidden",
                        preprocessResponse(prettyPrint())));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "job-seeking-post-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    /** 구직글을 생성하고 jobSeekingPostId를 반환한다. */
    private String createJobSeekingPost(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullCreateBody(false))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    /** CreateJobSeekingPostRequest의 모든 필드를 채운 요청 바디를 반환한다. */
    private Map<String, Object> fullCreateBody(boolean publish) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "웹툰 일러스트레이터 구직");
        body.put("roles", List.of("일러스트", "채색"));
        body.put("genres", List.of("판타지", "액션"));
        body.put("drawingStyle", "반실사체");
        body.put("preferredFeedbackStyle", "DETAILED");
        body.put("workStyle", "COLLABORATIVE");
        body.put("desiredRate", "페이지당 5만원");
        body.put("portfolioDescription", "5년차 웹툰 일러스트레이터입니다");
        body.put("referenceImages", List.of("https://img.example/portfolio1.png"));
        body.put("publish", publish);
        return body;
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
            String countryCode
    ) {}
}
