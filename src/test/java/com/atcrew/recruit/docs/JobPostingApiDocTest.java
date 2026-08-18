package com.atcrew.recruit.docs;

import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 구인글(JobPosting) API 문서화 통합 테스트.
 *
 * <p>전체 Spring 컨텍스트(MariaDB Testcontainer)를 기동해 작성→제출→관리자 승인→조회→끌어올리기→
 * 마감→삭제→복구로 이어지는 구인글 생명주기 전체를 REST Docs 스니펫으로 생성한다.
 */
class JobPostingApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    EntitlementBalanceRepository balanceRepository;

    @Test
    void 구인글_생성부터_복구까지_전체_플로우_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("구인글작성자");

        // 1. 작성 (DRAFT)
        MvcResult createResult = mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createJobPostingBody(false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andDo(document("recruit/create-job-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        relaxedRequestFields(
                                fieldWithPath("title").description("공고 제목 (최대 200자)"),
                                fieldWithPath("companyName").description("회사명"),
                                fieldWithPath("roles").description("모집 역할 목록 — 정본 담당 업무 enum 이름 (TOTAL_ARTWORK·LINEART·COLORING·BACKGROUND 등 ArtworkRole 22종)"),
                                fieldWithPath("genres").description("모집 장르 목록 — 정본 장르 enum 이름 (FANTASY·ROMANCE_FANTASY·ACTION·BL 등 Genre 29종)"),
                                fieldWithPath("deadline").description("마감일 (yyyy-MM-dd, null이면 상시모집)"),
                                fieldWithPath("recruitCount").description("모집 인원"),
                                fieldWithPath("employmentType").description("고용 형태 (FULL_TIME·OUTSOURCING·CONTRACT_TO_FULL·CONTRACT)"),
                                fieldWithPath("workLocationType").description("근무지 형태 (OFFICE·REMOTE·HYBRID)"),
                                fieldWithPath("workScheduleType").description("근무 형태 (FIXED·FLEXIBLE)"),
                                fieldWithPath("paymentType").description("급여 지급 방식 (ANNUAL_SALARY·PIECE_RATE·MG_RS)"),
                                fieldWithPath("paymentUnit").description("급여 지급 단위"),
                                fieldWithPath("minAmount").description("최소 금액"),
                                fieldWithPath("maxAmount").description("최대 금액"),
                                fieldWithPath("isNegotiable").description("협의 가능 여부"),
                                fieldWithPath("submit").description("true면 저장 직후 PENDING 제출까지 처리")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구인글 ID"),
                                fieldWithPath("data.authorMemberId").description("작성자 Member ID"),
                                fieldWithPath("data.authorName").description("작성자 표시명"),
                                fieldWithPath("data.title").description("공고 제목"),
                                fieldWithPath("data.status").description("상태 (DRAFT·PENDING·PUBLISHED·CLOSED·DELETED)"),
                                fieldWithPath("data.minAmount").description("최소 금액"),
                                fieldWithPath("data.maxAmount").description("최대 금액"),
                                fieldWithPath("data.bookmarkCount").description("북마크 수"),
                                fieldWithPath("data.viewCount").description("조회 수"),
                                fieldWithPath("data.boostedUntil").description("끌어올리기 만료 시각 (null이면 적용 안됨)"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601, UTC)"),
                                fieldWithPath("data.updatedAt").description("수정 시각 (ISO 8601, UTC)")
                        )
                ))
                .andReturn();
        String jobPostingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 2. 내 목록 조회
        mockMvc.perform(get("/api/recruit/job-postings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andDo(document("recruit/get-my-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구인글 ID"),
                                fieldWithPath("data.items[].title").description("공고 제목"),
                                fieldWithPath("data.items[].status").description("상태"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 3. 수정
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("title", "수정된 웹툰 채색 작가 구인");
        updateBody.put("recruitCount", 3);
        mockMvc.perform(put("/api/recruit/job-postings/{jobPostingId}", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 웹툰 채색 작가 구인"))
                .andExpect(jsonPath("$.data.recruitCount").value(3))
                .andDo(document("recruit/update-job-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedRequestFields(
                                fieldWithPath("title").description("공고 제목. null이면 변경 없음").optional(),
                                fieldWithPath("recruitCount").description("모집 인원. null이면 변경 없음").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.title").description("변경된 공고 제목"),
                                fieldWithPath("data.recruitCount").description("변경된 모집 인원")
                        )
                ));

        // 4. 제출 (DRAFT/PENDING → PENDING)
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/submit", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andDo(document("recruit/submit-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.status").description("변경된 상태 (PENDING)")
                        )
                ));

        // 5. [관리자] PENDING 목록 조회
        String adminToken = registerMemberAndGetToken("관리자계정");
        mockMvc.perform(get("/api/recruit/admin/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(jobPostingId))
                .andDo(document("recruit/admin-get-pending-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구인글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (PENDING)"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 6. [관리자] 승인 (PENDING → PUBLISHED)
        mockMvc.perform(patch("/api/recruit/admin/job-postings/{jobPostingId}/approve", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andDo(document("recruit/admin-approve-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.status").description("변경된 상태 (PUBLISHED)")
                        )
                ));

        // 7. 상세 조회 (인증 불필요)
        mockMvc.perform(get("/api/recruit/job-postings/{jobPostingId}", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobPostingId))
                .andDo(document("recruit/get-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("구인글 ID"),
                                fieldWithPath("data.title").description("공고 제목"),
                                fieldWithPath("data.status").description("상태 (PUBLISHED)"),
                                fieldWithPath("data.viewCount").description("조회 수")
                        )
                ));

        // 8. 목록 조회(커서, 인증 불필요) — 전역 공개 목록이라 다른 테스트가 발행한 구인글과 공존할 수 있으므로
        // 정확한 개수 대신 방금 승인한 구인글이 포함돼 있는지로 검증한다.
        mockMvc.perform(get("/api/recruit/job-postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='" + jobPostingId + "')]").exists())
                .andDo(document("recruit/get-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구인글 ID"),
                                fieldWithPath("data.items[].title").description("공고 제목"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 9. 끌어올리기 (48시간 상단 고정)
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/boost", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boostedUntil").isNotEmpty())
                .andDo(document("recruit/boost-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.boostedUntil").description("끌어올리기 만료 시각 (적용 시각 + 48시간)")
                        )
                ));

        // 10. 상태 변경 (마감)
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/status", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andDo(document("recruit/close-job-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        requestFields(
                                fieldWithPath("status").description("변경할 상태 (현재는 CLOSED만 지원)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.status").description("변경된 상태 (CLOSED)")
                        )
                ));

        // 11. 삭제 (휴지통 이동)
        mockMvc.perform(delete("/api/recruit/job-postings/{jobPostingId}", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/delete-job-posting",
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        )
                ));

        // 12. 휴지통 목록 조회
        mockMvc.perform(get("/api/recruit/job-postings/trash")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(jobPostingId))
                .andDo(document("recruit/get-trashed-job-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("구인글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (DELETED)"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 13. 복구 (DELETED → DRAFT)
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/restore", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andDo(document("recruit/restore-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.status").description("변경된 상태 (DRAFT)")
                        )
                ));
    }

    @Test
    void 관리자_구인글_반려_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("반려대상작성자");

        MvcResult createResult = mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createJobPostingBody(true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String jobPostingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        String adminToken = registerMemberAndGetToken("반려처리관리자");
        mockMvc.perform(patch("/api/recruit/admin/job-postings/{jobPostingId}/reject", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andDo(document("recruit/admin-reject-job-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("jobPostingId").description("구인글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.status").description("변경된 상태 (CLOSED, 반려)")
                        )
                ));
    }

    @Test
    void 타인_구인글_수정시_403_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("원작성자");
        String otherToken = registerMemberAndGetToken("다른회원");

        MvcResult createResult = mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createJobPostingBody(false))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobPostingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        mockMvc.perform(put("/api/recruit/job-postings/{jobPostingId}", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "권한없는 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_AUTHOR"))
                .andDo(document("recruit/update-job-posting-forbidden", preprocessResponse(prettyPrint())));
    }

    @Test
    void 끌어올리기_적용중_재요청시_409_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("끌올유저");

        MvcResult createResult = mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createJobPostingBody(false))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobPostingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/boost", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/boost", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOST_COOLDOWN"))
                .andDo(document("recruit/boost-job-posting-cooldown", preprocessResponse(prettyPrint())));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "recruit-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 구인글은 유료 단건 게시 상품이라 게시·끌어올리기에 보유 개수가 필요하다(구인구직-R02).
        BillingTestSupport.grantAllPostingProducts(balanceRepository,
                objectMapper.readTree(body).at("/data/member/id").asText());
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    /** 구인글 작성 요청 바디를 생성한다. */
    private Map<String, Object> createJobPostingBody(boolean submit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "웹툰 채색 작가 구인");
        body.put("companyName", "앳크루스튜디오");
        body.put("ceoName", "홍길동");
        body.put("industry", "웹툰");
        body.put("address", "서울시 강남구");
        body.put("contact", "010-1234-5678");
        body.put("websiteUrl", "https://atcrew.co.kr");
        body.put("companyDescription", "웹툰 제작 스튜디오입니다.");
        body.put("isBusinessRegistered", true);
        body.put("isResumeRequired", true);
        body.put("isCoverLetterRequired", false);
        body.put("roles", List.of("COLORING"));
        body.put("genres", List.of("ROMANCE_FANTASY"));
        body.put("workScope", "표지 채색 및 배경 작업");
        body.put("deadline", "2099-12-31");
        body.put("recruitCount", 2);
        body.put("hiringProcess", "서류 → 포트폴리오 검토 → 계약");
        body.put("education", "무관");
        body.put("experience", "경력 2년 이상");
        body.put("age", "무관");
        body.put("gender", "무관");
        body.put("employmentType", "OUTSOURCING");
        body.put("workLocationType", "REMOTE");
        body.put("workScheduleType", "FIXED");
        body.put("coreTimeStart", null);
        body.put("coreTimeEnd", null);
        body.put("hasOvertimePay", false);
        body.put("hasSocialInsurance", false);
        body.put("hasContract", true);
        body.put("paymentType", "PIECE_RATE");
        body.put("paymentUnit", "PER_CUT");
        body.put("minAmount", 3000L);
        body.put("maxAmount", 5000L);
        body.put("isNegotiable", true);
        body.put("mgAmount", null);
        body.put("rsRatio", null);
        body.put("hasBuyout", false);
        body.put("benefitDescription", "4대보험, 장비지원");
        body.put("benefitKeywords", List.of("4대보험", "장비지원"));
        body.put("thumbnailImage", "https://cdn.atcrew.co.kr/job/thumb.png");
        body.put("referenceImages", List.of("https://cdn.atcrew.co.kr/job/ref1.png"));
        body.put("submit", submit);
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
