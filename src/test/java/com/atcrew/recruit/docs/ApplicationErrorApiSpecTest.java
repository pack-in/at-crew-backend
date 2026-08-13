package com.atcrew.recruit.docs;

import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import com.atcrew.recruit.RecruitService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지원/지원자 관리(Application) API 에러 응답 문서화 테스트 (docs/design/recruit-module-design.md §2.4, §2.5, §2.6).
 *
 * <p>{@code MockMvcRestDocumentationWrapper.document()} + {@code ResourceDocumentation.resource()}
 * 조합을 사용해 openapi3 태스크가 읽을 수 있는 resource.json을 생성한다(restdocs-api-spec 파이프라인).
 * APPLICATION_NOT_FOUND, APPLICATION_NOT_ALLOWED, DUPLICATE_APPLICATION, SELF_APPLICATION_NOT_ALLOWED를
 * 대표로 커버한다. 구인글을 PUBLISHED로 만들려면 작성→제출→관리자 승인 흐름이 필요하므로
 * REST 엔드포인트 대신 {@link RecruitService}를 직접 호출해 테스트 데이터를 준비한다.
 */
class ApplicationErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    RecruitService recruitService;

    @Test
    void 존재하지_않는_지원_내역_상태변경시_404_APPLICATION_NOT_FOUND() throws Exception {
        RegisteredMember owner = registerMember("지원없음구인글작성자");
        JobPostingInfo jobPosting = createPublishedJobPosting(owner.memberId());

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}/review-status",
                        jobPosting.id(), UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reviewStatus", "REVIEWING"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
                .andDo(document("recruit/update-job-application-review-status-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 지원 ID로 채용 단계 변경 시도 — 404 APPLICATION_NOT_FOUND")));
    }

    @Test
    void 미게시_구인글에_지원시_400_APPLICATION_NOT_ALLOWED() throws Exception {
        RegisteredMember owner = registerMember("임시저장구인글작성자");
        RegisteredMember applicant = registerMember("미게시글지원자");

        // submit=false로 저장해 DRAFT 상태로 남긴다 — PUBLISHED가 아니므로 지원이 거부돼야 한다.
        JobPostingInfo draftJobPosting = recruitService.createJobPosting(owner.memberId(), jobPostingCommand(false));

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", draftJobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serialExperience", "NEWCOMER", "assistantExperience", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_ALLOWED"))
                .andDo(document("recruit/apply-job-posting-not-allowed",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("아직 게시(PUBLISHED)되지 않은 구인글에 지원 시도 — 400 APPLICATION_NOT_ALLOWED")));
    }

    @Test
    void 구인글_중복_지원시_409_DUPLICATE_APPLICATION() throws Exception {
        RegisteredMember owner = registerMember("중복지원구인글작성자");
        RegisteredMember applicant = registerMember("중복지원자스펙");
        JobPostingInfo jobPosting = createPublishedJobPosting(owner.memberId());

        String applyBody = objectMapper.writeValueAsString(Map.of(
                "serialExperience", "NEWCOMER", "assistantExperience", false));

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"))
                .andDo(document("recruit/apply-job-posting-duplicate-spec",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미 지원한 구인글에 재지원 시도 — 409 DUPLICATE_APPLICATION")));
    }

    @Test
    void 본인_구인글에_지원시_400_SELF_APPLICATION_NOT_ALLOWED() throws Exception {
        RegisteredMember owner = registerMember("본인지원구인글작성자스펙");
        JobPostingInfo jobPosting = createPublishedJobPosting(owner.memberId());

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serialExperience", "NEWCOMER", "assistantExperience", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_APPLICATION_NOT_ALLOWED"))
                .andDo(document("recruit/apply-job-posting-self-application-spec",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("본인이 작성한 구인글에 지원 시도 — 400 SELF_APPLICATION_NOT_ALLOWED")));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "application-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();
        String memberId = objectMapper.readTree(body).at("/data/member/id").asText();
        return new RegisteredMember(accessToken, memberId);
    }

    /** RecruitService를 직접 호출해 구인글을 작성(submit=true로 즉시 PENDING)→승인하여 PUBLISHED 상태로 만든다. */
    private JobPostingInfo createPublishedJobPosting(String ownerMemberId) {
        JobPostingInfo created = recruitService.createJobPosting(ownerMemberId, jobPostingCommand(true));
        return recruitService.approveJobPosting(created.id());
    }

    private CreateJobPostingCommand jobPostingCommand(boolean submit) {
        return new CreateJobPostingCommand(
                "백엔드 개발자 구인", "앳크루스튜디오", "김대표", "IT", "서울특별시", "010-1111-2222",
                "https://atcrew.co.kr", "웹툰 플랫폼을 만드는 스튜디오입니다", true, true, false,
                List.of("배경"), List.of("웹툰"), "배경 작업 전반", null, 1, "서류 -> 면접",
                null, null, null, null, JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE,
                JobWorkScheduleType.FIXED, null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 30_000_000L, 40_000_000L, false,
                null, null, false, null, List.of(), null, List.of(), submit);
    }

    /** 회원가입으로 발급받은 액세스 토큰과 회원 ID */
    private record RegisteredMember(String accessToken, String memberId) {
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
