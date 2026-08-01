package com.atcrew.recruit.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.recruit.RecruitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LikedArtistController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 * 요청 바디가 없는 엔드포인트가 대부분이므로 경로변수 {@code artistMemberId}의 형식 검증에 집중한다.
 * ({@code MEMBER_ID_PATTERN} — MongoDB ObjectId 24자 hex 또는 UUID 36자만 허용)
 */
@ExtendWith(RestDocumentationExtension.class)
class LikedArtistControllerValidationTest {

    private static final String INVALID_MEMBER_ID = "invalid-member-id";

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    RecruitService recruitService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        recruitService = mock(RecruitService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // 컨트롤러의 클래스 레벨 @Validated(경로변수 @Pattern)는 실제 애플리케이션에서는
        // Spring Boot의 MethodValidationPostProcessor가 AOP 프록시로 감싸야 동작한다.
        // standaloneSetup은 빈 후처리를 거치지 않으므로 여기서 동일한 프록시를 수동으로 적용한다.
        mockMvc = MockMvcBuilders
                .standaloneSetup(applyMethodValidation(new LikedArtistController(recruitService, securityUtils)))
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
        return postProcessor.postProcessAfterInitialization(controller, "likedArtistController");
    }

    @Test
    void 관심작가_저장_작가_ID_형식_위반_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/liked-artists/{artistMemberId}", INVALID_MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/like-artist-invalid-id-format"));
    }

    @Test
    void 관심작가_해제_작가_ID_형식_위반_거부() throws Exception {
        mockMvc.perform(delete("/api/recruit/liked-artists/{artistMemberId}", INVALID_MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/unlike-artist-invalid-id-format"));
    }

    @Test
    void 관심작가_목록_검색어_길이_초과_거부() throws Exception {
        mockMvc.perform(get("/api/recruit/liked-artists").param("q", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/list-liked-artists-keyword-too-long"));
    }

    @Test
    void 작가_조회_기록_작가_ID_형식_위반_거부() throws Exception {
        mockMvc.perform(post("/api/recruit/recently-viewed-artists/{artistMemberId}", INVALID_MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("recruit/validation/record-artist-view-invalid-id-format"));
    }
}
