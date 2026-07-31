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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApplicationController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 * 구인글/팀원모집글 지원 요청은 동일한 요청 DTO({@code CreateApplicationRequest},
 * {@code UpdateApplicationReviewStatusRequest})를 공유하므로 구인글 엔드포인트를 기준으로 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class ApplicationControllerValidationTest {

    private static final String VALID_JOB_POSTING_ID = "018f4c2e-1234-7abc-8def-1234567890ab";
    private static final String VALID_APPLICATION_ID = "018f4c2e-5678-7abc-8def-1234567890ab";

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    RecruitService recruitService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        recruitService = mock(RecruitService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // 컨트롤러의 클래스 레벨 @Validated(경로변수 @Pattern 등)는 실제 애플리케이션에서는
        // Spring Boot의 MethodValidationPostProcessor가 AOP 프록시로 감싸야 동작한다.
        // standaloneSetup은 빈 후처리를 거치지 않으므로 여기서 동일한 프록시를 수동으로 적용한다.
        mockMvc = MockMvcBuilders
                .standaloneSetup(applyMethodValidation(new ApplicationController(recruitService, securityUtils)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    private Object applyMethodValidation(Object controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MethodValidationPostProcessor postProcessor = new MethodValidationPostProcessor();
        postProcessor.setValidator(validator);
        postProcessor.afterPropertiesSet();
        return postProcessor.postProcessAfterInitialization(controller, "applicationController");
    }

    // ─── CreateApplicationRequest ─────────────────────────────────────

    @Test
    void 구인글_지원_연재경험_누락_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialExperience", null);
        body.put("assistantExperience", true);

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", VALID_JOB_POSTING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/apply-job-posting-missing-serial-experience"));
    }

    @Test
    void 구인글_지원_연재경험_존재하지_않는_enum_값_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialExperience", "INVALID_EXPERIENCE");
        body.put("assistantExperience", true);

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", VALID_JOB_POSTING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/apply-job-posting-invalid-serial-experience-enum"));
    }

    @Test
    void 구인글_지원_이력서_URL_500자_초과_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialExperience", "NEWCOMER");
        body.put("assistantExperience", true);
        body.put("resumeUrl", "a".repeat(501));

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", VALID_JOB_POSTING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/apply-job-posting-resume-url-too-long"));
    }

    @Test
    void 구인글_지원_구인글_ID_형식_위반_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialExperience", "NEWCOMER");
        body.put("assistantExperience", true);

        mockMvc.perform(post("/api/recruit/job-postings/{jobPostingId}/applications", "invalid-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/apply-job-posting-invalid-id-format"));
    }

    @Test
    void 팀원모집글_지원_팀원모집글_ID_형식_위반_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialExperience", "NEWCOMER");
        body.put("assistantExperience", true);

        mockMvc.perform(post("/api/recruit/team-postings/{teamPostingId}/applications", "invalid-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/apply-team-posting-invalid-id-format"));
    }

    // ─── UpdateApplicationReviewStatusRequest ──────────────────────────

    @Test
    void 지원_채용단계_변경_reviewStatus_누락_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewStatus", null);

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}/review-status",
                        VALID_JOB_POSTING_ID, VALID_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-review-status-missing"));
    }

    @Test
    void 지원_채용단계_변경_존재하지_않는_enum_값_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewStatus", "INVALID_STATUS");

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}/review-status",
                        VALID_JOB_POSTING_ID, VALID_APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-review-status-invalid-enum"));
    }

    @Test
    void 지원_채용단계_변경_지원_ID_형식_위반_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewStatus", "REVIEWING");

        mockMvc.perform(patch("/api/recruit/job-postings/{jobPostingId}/applications/{applicationId}/review-status",
                        VALID_JOB_POSTING_ID, "invalid-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-review-status-invalid-application-id-format"));
    }
}
