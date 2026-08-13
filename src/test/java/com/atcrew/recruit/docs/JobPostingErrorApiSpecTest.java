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

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구인글(JobPosting) API 에러 응답 문서화 테스트.
 *
 * <p>{@code MockMvcRestDocumentationWrapper.document()} + {@code ResourceDocumentation.resource()}
 * 조합을 사용해 openapi3 태스크가 읽을 수 있는 resource.json을 생성한다(restdocs-api-spec 파이프라인).
 * RecruitErrorCode 중 이 컨트롤러 경로로 가장 간단히 재현 가능한 코드를 대표로 커버한다:
 * JOB_POSTING_NOT_FOUND, INVALID_AMOUNT_RANGE, INVALID_STATUS_TRANSITION, FORBIDDEN_NOT_AUTHOR, BOOST_COOLDOWN,
 * INVALID_CURSOR.
 */
class JobPostingErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 잘못된_커서_형식으로_목록_조회시_400_INVALID_CURSOR() throws Exception {
        mockMvc.perform(get("/api/recruit/job-postings").param("cursor", "not-a-valid-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"))
                .andDo(document("recruit/list-job-postings-invalid-cursor",
                        preprocessResponse(prettyPrint()),
                        resource("구분자(_)가 없는 잘못된 형식의 커서로 구인글 목록 조회 — 400 INVALID_CURSOR")));
    }

    @Test
    void 존재하지_않는_구인글_조회시_404_JOB_POSTING_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/recruit/job-postings/{jobPostingId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_NOT_FOUND"))
                .andDo(document("recruit/get-job-posting-not-found",
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 구인글 ID로 상세 조회 — 404 JOB_POSTING_NOT_FOUND")));
    }

    @Test
    void 최소금액이_최대금액보다_크면_400_INVALID_AMOUNT_RANGE() throws Exception {
        String authorToken = registerMemberAndGetToken("금액역전작성자");

        Map<String, Object> body = createJobPostingBody(false);
        body.put("minAmount", 5_000_000L);
        body.put("maxAmount", 3_000_000L);

        mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT_RANGE"))
                .andDo(document("recruit/create-job-posting-invalid-amount-range",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("최소 금액이 최대 금액보다 큰 구인글 작성 시도 — 400 INVALID_AMOUNT_RANGE")));
    }

    @Test
    void CLOSED가_아닌_상태로_변경_요청시_400_INVALID_STATUS_TRANSITION() throws Exception {
        String authorToken = registerMemberAndGetToken("상태전이작성자");
        String jobPostingId = createJobPosting(authorToken);

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/status", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DRAFT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andDo(document("recruit/update-job-posting-status-invalid-transition",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("현재 지원하지 않는 상태(CLOSED 이외)로 구인글 상태 변경 시도 — 400 INVALID_STATUS_TRANSITION")));
    }

    @Test
    void 타인_구인글_수정시_403_FORBIDDEN_NOT_AUTHOR() throws Exception {
        String authorToken = registerMemberAndGetToken("원작성자");
        String otherToken = registerMemberAndGetToken("타인수정시도자");
        String jobPostingId = createJobPosting(authorToken);

        mockMvc.perform(put("/api/recruit/job-postings/{jobPostingId}", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "권한없는 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_AUTHOR"))
                .andDo(document("recruit/update-job-posting-forbidden-not-author",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("작성자가 아닌 회원이 구인글 수정 시도 — 403 FORBIDDEN_NOT_AUTHOR")));
    }

    @Test
    void 끌어올리기_적용중_재요청시_409_BOOST_COOLDOWN() throws Exception {
        String authorToken = registerMemberAndGetToken("끌올재요청유저");
        String jobPostingId = createJobPosting(authorToken);

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/boost", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/boost", jobPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOST_COOLDOWN"))
                .andDo(document("recruit/boost-job-posting-cooldown-spec",
                        preprocessResponse(prettyPrint()),
                        resource("이미 끌어올리기가 적용 중인 구인글에 재요청 — 409 BOOST_COOLDOWN")));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "job-posting-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    /** 구인글을 생성하고 jobPostingId를 반환한다. */
    private String createJobPosting(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recruit/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createJobPostingBody(false))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
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
        body.put("roles", List.of("채색"));
        body.put("genres", List.of("로맨스"));
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
