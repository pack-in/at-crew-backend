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

import java.util.ArrayList;
import java.util.List;
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
 * JobSeekingPostController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class JobSeekingPostControllerValidationTest {

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
        // 컨트롤러가 클래스 레벨 @Validated이므로 @PathVariable @Pattern 검증은 AOP 프록시를 통해서만 동작한다.
        // standaloneSetup은 빈 후처리기를 거치지 않으므로 MethodValidationPostProcessor로 직접 프록시를 생성한다.
        MethodValidationPostProcessor methodValidationPostProcessor = new MethodValidationPostProcessor();
        methodValidationPostProcessor.afterPropertiesSet();
        Object controller = methodValidationPostProcessor.postProcessAfterInitialization(
                new JobSeekingPostController(recruitService, securityUtils), "jobSeekingPostController");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── CreateJobSeekingPostRequest ────────────────────────────────────

    @Test
    void 작성_제목_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "   ", "publish", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-seeking-post-blank-title"));
    }

    @Test
    void 작성_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "가".repeat(201), "publish", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-seeking-post-title-too-long"));
    }

    @Test
    void 작성_희망역할_21개_초과_거부() throws Exception {
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            roles.add("역할" + i);
        }
        mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구직글 제목", "publish", false, "roles", roles))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-seeking-post-too-many-roles"));
    }

    @Test
    void 작성_희망역할_빈_문자열_포함_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구직글 제목", "publish", false, "roles", List.of("일러스트", "   ")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-seeking-post-blank-role"));
    }

    @Test
    void 작성_포트폴리오_소개_5000자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/job-seeking-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "구직글 제목", "publish", false, "portfolioDescription", "a".repeat(5001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-job-seeking-post-portfolio-description-too-long"));
    }

    // ─── UpdateJobSeekingPostRequest ─────────────────────────────────────

    @Test
    void 수정_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/job-seeking-posts/{jobSeekingPostId}", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "가".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-seeking-post-title-too-long"));
    }

    @Test
    void 수정_참고이미지_URL_500자_초과_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/job-seeking-posts/{jobSeekingPostId}", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("referenceImages", List.of("https://example.com/" + "a".repeat(500))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-seeking-post-reference-image-too-long"));
    }

    @Test
    void 수정_경로변수_UUID_형식_위반_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/job-seeking-posts/{jobSeekingPostId}", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "수정된 제목"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-seeking-post-invalid-path-uuid"));
    }

    // ─── 경로변수 UUID 패턴 (본문 없는 엔드포인트) ──────────────────────────

    @Test
    void 게시_경로변수_UUID_형식_위반_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/publish", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/publish-job-seeking-post-invalid-path-uuid"));
    }

    // ─── UpdateJobSeekingPostStatusRequest ───────────────────────────────

    @Test
    void 상태변경_status_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/status", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-job-seeking-post-status-missing"));
    }

    @Test
    void 상태변경_CLOSED가_아닌_값_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/job-seeking-posts/{jobSeekingPostId}/status", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andDo(document("recruit/validation/update-job-seeking-post-status-invalid-transition"));
    }
}
