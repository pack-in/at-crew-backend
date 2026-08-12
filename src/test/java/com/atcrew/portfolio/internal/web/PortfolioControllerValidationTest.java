package com.atcrew.portfolio.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PortfolioController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 * 경로변수는 {@code UUID_PATTERN}, 공유 식별자는 {@code SHARED_IDENTIFIER_PATTERN}
 * (슬러그·handle 공용 — {@code [A-Za-z0-9_-]{3,64}})을 따른다.
 */
@ExtendWith(RestDocumentationExtension.class)
class PortfolioControllerValidationTest {

    private static final String VALID_ID = UUID.randomUUID().toString();

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    PortfolioServiceImpl portfolioService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        portfolioService = mock(PortfolioServiceImpl.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // 컨트롤러의 클래스 레벨 @Validated(경로변수 @Pattern)는 실제 애플리케이션에서는
        // Spring Boot의 MethodValidationPostProcessor가 AOP 프록시로 감싸야 동작한다.
        // standaloneSetup은 빈 후처리를 거치지 않으므로 여기서 동일한 프록시를 수동으로 적용한다.
        mockMvc = MockMvcBuilders
                .standaloneSetup(applyMethodValidation(new PortfolioController(portfolioService, securityUtils)))
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
        return postProcessor.postProcessAfterInitialization(controller, "portfolioController");
    }

    // ─── CreatePortfolioRequest ─────────────────────────────────────────

    @Test
    void 생성_제목_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("title", "   ", "reflectionType", "LIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/create-portfolio-blank-title"));
    }

    @Test
    void 생성_제목_누락_거부() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reflectionType", "LIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/create-portfolio-missing-title"));
    }

    @Test
    void 생성_반영유형_누락_거부() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "포트폴리오 제목"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/create-portfolio-missing-reflection-type"));
    }

    @Test
    void 생성_작품_ID_빈_문자열_포함_거부() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "포트폴리오 제목");
        body.put("reflectionType", "LIVE");
        body.put("artworkIds", List.of(VALID_ID, "   "));

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/create-portfolio-blank-artwork-id"));
    }

    // ─── 경로변수 UUID 패턴 ──────────────────────────────────────────────

    @Test
    void 수정_경로변수_UUID_형식_위반_거부() throws Exception {
        mockMvc.perform(patch("/api/portfolios/{portfolioId}", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "수정된 제목"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/update-portfolio-invalid-path-uuid"));
    }

    // ─── AddPortfolioArtworksRequest ────────────────────────────────────

    @Test
    void 작품_추가_빈_배열_거부() throws Exception {
        mockMvc.perform(post("/api/portfolios/{portfolioId}/artworks", VALID_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("artworkIds", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/add-artworks-empty-list"));
    }

    // ─── 공유 식별자 패턴 ────────────────────────────────────────────────

    @Test
    void 공유_열람_식별자_길이_미달_거부() throws Exception {
        mockMvc.perform(get("/api/portfolios/shared/{identifier}", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/get-shared-portfolio-identifier-too-short"));
    }

    @Test
    void 공유_열람_식별자_허용되지_않는_문자_거부() throws Exception {
        mockMvc.perform(get("/api/portfolios/shared/{identifier}", "invalid!identifier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("portfolio/validation/get-shared-portfolio-identifier-invalid-chars"));
    }
}
