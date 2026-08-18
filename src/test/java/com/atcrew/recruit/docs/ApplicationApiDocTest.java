package com.atcrew.recruit.docs;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import com.atcrew.recruit.RecruitService;
import com.atcrew.recruit.TeamActivityDuration;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamWeeklyActivityTime;
import com.atcrew.recruit.TeamWorkLocationType;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지원/지원자 관리 API 문서화 통합 테스트 (docs/design/recruit-module-design.md §2.4, §2.5, §2.6).
 *
 * <p>구인글/팀원모집글을 PUBLISHED 상태로 만들려면 작성→(구인글은 제출→관리자 승인) 흐름이 필요하므로,
 * REST 엔드포인트 대신 {@link RecruitService}를 직접 호출해 테스트 데이터를 준비한다.
 */
class ApplicationApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    EntitlementBalanceRepository balanceRepository;

    @Autowired
    RecruitService recruitService;

    @Test
    void 구인글_지원_지원자목록_상태변경_취소_문서화() throws Exception {
        RegisteredMember owner = registerMember("구인글작성자");
        RegisteredMember applicant = registerMember("구인글지원자");

        JobPostingInfo jobPosting = createPublishedJobPosting(owner.memberId());

        MvcResult applyResult = mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serialExperience", "THREE_TO_FOUR",
                                "assistantExperience", true,
                                "resumeUrl", "https://atcrew.co.kr/resume/sample.pdf"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reviewStatus").value("RECEIVED"))
                .andDo(document("recruit/apply-job-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("serialExperience").description("연재 경험 (NEWCOMER·ONE_TO_TWO·THREE_TO_FOUR·FIVE_PLUS)"),
                                fieldWithPath("assistantExperience").description("어시스턴트 경험 여부"),
                                fieldWithPath("resumeUrl").description("이력서 URL (선택, 최대 500자)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("지원 ID"),
                                fieldWithPath("data.postingId").description("지원 대상 구인글 ID"),
                                fieldWithPath("data.applicantMemberId").description("지원자 Member ID"),
                                fieldWithPath("data.applicantName").description("지원자 표시명").optional(),
                                fieldWithPath("data.serialExperience").description("연재 경험"),
                                fieldWithPath("data.assistantExperience").description("어시스턴트 경험 여부"),
                                fieldWithPath("data.resumeUrl").description("이력서 URL").optional(),
                                fieldWithPath("data.reviewStatus").description("채용 단계 상태 (기본 RECEIVED)"),
                                fieldWithPath("data.appliedAt").description("지원 시각 (ISO 8601)")
                        )
                ))
                .andReturn();
        String applicationId = objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        mockMvc.perform(get("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andDo(document("recruit/list-job-posting-applications",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("지원 ID"),
                                fieldWithPath("data.items[].applicantMemberId").description("지원자 Member ID"),
                                fieldWithPath("data.items[].reviewStatus").description("채용 단계 상태"),
                                fieldWithPath("data.nextCursor").type(JsonFieldType.STRING)
                                        .description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}/review-status",
                        jobPosting.id(), applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reviewStatus", "REVIEWING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWING"))
                .andDo(document("recruit/update-job-application-review-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("reviewStatus").description("변경할 채용 단계 (RECEIVED·REVIEWING·ACCEPTED·REJECTED)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("지원 ID"),
                                fieldWithPath("data.reviewStatus").description("변경된 채용 단계 상태")
                        )
                ));

        mockMvc.perform(delete("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}",
                        jobPosting.id(), applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/delete-job-application"));
    }

    @Test
    void 팀원모집글_지원_성공_문서화() throws Exception {
        RegisteredMember owner = registerMember("팀원모집글작성자");
        RegisteredMember applicant = registerMember("팀원모집글지원자");

        TeamPostingInfo teamPosting = createPublishedTeamPosting(owner.memberId());

        mockMvc.perform(post("/api/recruit/team-postings/{teamPostingId}/applications", teamPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicant.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serialExperience", "NEWCOMER",
                                "assistantExperience", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.postingId").value(teamPosting.id()))
                .andDo(document("recruit/apply-team-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("serialExperience").description("연재 경험 (NEWCOMER·ONE_TO_TWO·THREE_TO_FOUR·FIVE_PLUS)"),
                                fieldWithPath("assistantExperience").description("어시스턴트 경험 여부"),
                                fieldWithPath("resumeUrl").type(JsonFieldType.STRING)
                                        .description("이력서 URL (선택, 최대 500자)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("지원 ID"),
                                fieldWithPath("data.postingId").description("지원 대상 팀원모집글 ID"),
                                fieldWithPath("data.applicantMemberId").description("지원자 Member ID"),
                                fieldWithPath("data.reviewStatus").description("채용 단계 상태 (기본 RECEIVED)"),
                                fieldWithPath("data.appliedAt").description("지원 시각 (ISO 8601)")
                        )
                ));
    }

    @Test
    void 구인글_중복_지원_409() throws Exception {
        RegisteredMember owner = registerMember("중복지원구인글작성자");
        RegisteredMember applicant = registerMember("중복지원자");
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
                .andDo(document("recruit/apply-job-posting-duplicate", preprocessResponse(prettyPrint())));
    }

    @Test
    void 구인글_본인글_지원_400() throws Exception {
        RegisteredMember owner = registerMember("본인지원구인글작성자");
        JobPostingInfo jobPosting = createPublishedJobPosting(owner.memberId());

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", jobPosting.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "serialExperience", "NEWCOMER", "assistantExperience", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_APPLICATION_NOT_ALLOWED"))
                .andDo(document("recruit/apply-job-posting-self-application", preprocessResponse(prettyPrint())));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "application-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
        // 구인글·팀원모집글은 유료 단건 게시 상품이다(구인구직-R02).
        BillingTestSupport.grantAllPostingProducts(balanceRepository, memberId);
        return new RegisteredMember(accessToken, memberId);
    }

    /** RecruitService를 직접 호출해 구인글을 작성→제출→승인하여 PUBLISHED 상태로 만든다. */
    private JobPostingInfo createPublishedJobPosting(String ownerMemberId) {
        recruitService.createJobPosting(ownerMemberId, jobPostingCommand());
        JobPostingInfo pending = recruitService.getMyJobPostings(ownerMemberId, null, 20).items().get(0);
        return recruitService.approveJobPosting(pending.id());
    }

    /** RecruitService를 직접 호출해 팀원모집글을 작성한다 — 승인 절차가 없어 저장 즉시 PUBLISHED다. */
    private TeamPostingInfo createPublishedTeamPosting(String ownerMemberId) {
        return recruitService.createTeamPosting(ownerMemberId, teamPostingCommand());
    }

    private CreateJobPostingCommand jobPostingCommand() {
        return new CreateJobPostingCommand(
                "백엔드 개발자 구인", "앳크루스튜디오", "김대표", "IT", "서울특별시", "010-1111-2222",
                "https://atcrew.co.kr", "웹툰 플랫폼을 만드는 스튜디오입니다", true, true, false,
                List.of(ArtworkRole.BACKGROUND), List.of(Genre.FANTASY), "배경 작업 전반", null, 1, "서류 -> 면접",
                null, null, null, null, JobEmploymentType.FULL_TIME, JobWorkLocationType.OFFICE,
                JobWorkScheduleType.FIXED, null, null, true, true, true,
                JobPaymentType.ANNUAL_SALARY, JobPaymentUnit.ANNUAL, 30_000_000L, 40_000_000L, false,
                null, null, false, null, List.of(), null, List.of(), true);
    }

    private CreateTeamPostingCommand teamPostingCommand() {
        return new CreateTeamPostingCommand(
                "판타지 웹툰 팀원 모집", false, false, false, "모집팀", "010-3333-4444",
                "판타지 장르 웹툰을 함께 만들 팀원을 모집합니다", List.of("취미"), TeamWorkLocationType.OFFLINE,
                "서울특별시", List.of(ArtworkRole.TOTAL_ARTWORK), List.of(Genre.FANTASY), false, true, null, null, 2,
                "서류 검토 후 개별 연락", TeamActivityDuration.THREE_MONTHS, TeamWeeklyActivityTime.FLEXIBLE,
                "완결까지 함께할 팀원을 찾습니다", null, List.of());
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
