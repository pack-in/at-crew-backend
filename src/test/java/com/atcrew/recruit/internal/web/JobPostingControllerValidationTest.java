package com.atcrew.recruit.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.recruit.RecruitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JobPostingController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class JobPostingControllerValidationTest {

    private static final String VALID_ID = UUID.randomUUID().toString();

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    RecruitService recruitService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        recruitService = mock(RecruitService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // 컨트롤러가 클래스 레벨 @Validated이므로 실제 애플리케이션 컨텍스트처럼
        // AOP 프록시로 감싸야 PathVariable/RequestParam 제약(@Pattern 등)이 검증된다.
        MethodValidationPostProcessor methodValidationPostProcessor = new MethodValidationPostProcessor();
        methodValidationPostProcessor.afterPropertiesSet();
        Object proxiedController = methodValidationPostProcessor.postProcessAfterInitialization(
                new JobPostingController(recruitService, securityUtils), "jobPostingController");
        mockMvc = MockMvcBuilders
                .standaloneSetup(proxiedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── CreateJobPostingRequest ────────────────────────────────────────

    @Test
    void 작성_제목_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "   ", "submit", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-posting-blank-title"));
    }

    @Test
    void 작성_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "가".repeat(201), "submit", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-posting-title-too-long"));
    }

    @Test
    void 작성_최소금액_음수_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구인글 제목", "submit", false, "minAmount", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-posting-negative-min-amount"));
    }

    @Test
    void 작성_마감일_과거_날짜_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구인글 제목", "submit", false, "deadline", "2000-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-posting-past-deadline"));
    }

    @Test
    void 작성_모집인원_0_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구인글 제목", "submit", false, "recruitCount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-posting-invalid-recruit-count"));
    }

    // ─── UpdateJobPostingRequest ────────────────────────────────────────

    @Test
    void 수정_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/job-postings/{jobPostingId}", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "가".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-posting-title-too-long"));
    }

    @Test
    void 수정_경로변수_UUID_형식_위반_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/job-postings/{jobPostingId}", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "수정된 제목"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-posting-invalid-path-uuid"));
    }

    // ─── UpdateJobPostingStatusRequest ──────────────────────────────────

    @Test
    void 상태변경_status_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/status", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-status-missing-status"));
    }

    @Test
    void 상태변경_CLOSED가_아닌_값_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/status", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DRAFT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andDo(document("recruit/validation/update-status-invalid-transition"));
    }
}
