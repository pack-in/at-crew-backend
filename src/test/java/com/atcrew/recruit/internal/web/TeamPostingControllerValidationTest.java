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

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TeamPostingController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class TeamPostingControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    RecruitService recruitService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        recruitService = mock(RecruitService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // 컨트롤러 클래스의 @Validated는 AOP 프록시를 통한 메서드 검증(@PathVariable @Pattern 등)에 위임되므로
        // standaloneSetup이 원본 인스턴스를 그대로 등록하기 전에 MethodValidationPostProcessor로 프록시를 만들어준다.
        MethodValidationPostProcessor methodValidationPostProcessor = new MethodValidationPostProcessor();
        methodValidationPostProcessor.afterPropertiesSet();
        Object teamPostingController = methodValidationPostProcessor.postProcessAfterInitialization(
                new TeamPostingController(recruitService, securityUtils), "teamPostingController");

        mockMvc = MockMvcBuilders
                .standaloneSetup(teamPostingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── CreateTeamPostingRequest ─────────────────────────────────────

    @Test
    void 작성_제목_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-blank-title"));
    }

    @Test
    void 작성_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "가".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-title-too-long"));
    }

    @Test
    void 작성_모집자_소개_5000자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "authorDescription", "가".repeat(5001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-author-description-too-long"));
    }

    @Test
    void 작성_모집_역할_원소_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "roles", List.of("   ")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-blank-role"));
    }

    @Test
    void 작성_모집_역할_21개_초과_거부() throws Exception {
        List<String> tooManyRoles = List.of(
                "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10",
                "r11", "r12", "r13", "r14", "r15", "r16", "r17", "r18", "r19", "r20", "r21");
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "roles", tooManyRoles))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-too-many-roles"));
    }

    @Test
    void 작성_마감일_과거_날짜_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "deadline", "2020-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-past-deadline"));
    }

    @Test
    void 작성_모집_인원_0_이하_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "recruitCount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-recruit-count-too-low"));
    }

    @Test
    void 작성_모집_인원_9999_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "recruitCount", 10000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-recruit-count-too-high"));
    }

    @Test
    void 작성_참고_이미지_URL_500자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/team-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "유효한 팀원모집글", "referenceImages", List.of("a".repeat(501))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/create-team-posting-reference-image-too-long"));
    }

    // ─── UpdateTeamPostingRequest ─────────────────────────────────────

    @Test
    void 수정_제목_200자_초과_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/team-postings/{teamPostingId}",
                        "018f4c2e-1234-7abc-8def-0123456789ab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "가".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-team-posting-title-too-long"));
    }

    @Test
    void 수정_모집_인원_0_이하_거부() throws Exception {
        mockMvc.perform(put("/api/recruit/team-postings/{teamPostingId}",
                        "018f4c2e-1234-7abc-8def-0123456789ab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recruitCount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-team-posting-recruit-count-too-low"));
    }

    // ─── UpdateTeamPostingStatusRequest ───────────────────────────────

    @Test
    void 상태_변경_상태값_누락_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/status",
                        "018f4c2e-1234-7abc-8def-0123456789ab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/update-team-posting-status-missing"));
    }

    @Test
    void 상태_변경_지원하지_않는_상태값_거부() throws Exception {
        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/status",
                        "018f4c2e-1234-7abc-8def-0123456789ab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DRAFT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andDo(document("recruit/validation/update-team-posting-status-unsupported-transition"));
    }

    // ─── PathVariable 형식 검증 ────────────────────────────────────────

    @Test
    void 상세_조회_팀원모집글_ID_형식_위반_거부() throws Exception {
        mockMvc.perform(get("/api/recruit/team-postings/{teamPostingId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/get-team-posting-invalid-id-format"));
    }
}
