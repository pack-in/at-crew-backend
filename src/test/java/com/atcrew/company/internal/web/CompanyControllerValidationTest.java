package com.atcrew.company.internal.web;

import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.atcrew.company.CompanyService;
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

import java.util.List;
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
 * CompanyController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class CompanyControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    CompanyService companyService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        companyService = mock(CompanyService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CompanyController(companyService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── CreateCompanyRequest ─────────────────────────────────────────

    @Test
    void 기업_생성_기업명_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/create-company-blank-name"));
    }

    @Test
    void 기업_생성_기업명_16자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "가".repeat(17)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/create-company-name-too-long"));
    }

    // ─── UpdateCompanyNameRequest ─────────────────────────────────────

    @Test
    void 기업명_수정_빈_문자열_거부() throws Exception {
        mockMvc.perform(patch("/api/companies/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-name-blank"));
    }

    // ─── UpdateCompanyInfoRequest ─────────────────────────────────────

    @Test
    void 정보_수정_정본_밖_활동_분야_거부() throws Exception {
        // 활동 분야는 단일 선택이며 정본은 ILLUSTRATION·WEBTOON·PRINT_COMIC·ANIMATION 4종뿐이다
        // (기획서 마이페이지_기업-R07, 정책 데이터구조-R03).
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activityField", "WEB_NOVEL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-info-invalid-activity-field"));
    }

    @Test
    void 정보_수정_활동_지역_7개_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activeRegions",
                                List.of("SEOUL", "GYEONGGI", "DAEJEON", "DAEGU", "GWANGJU", "BUSAN", "OTHER", "SEOUL")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-info-too-many-active-regions"));
    }

    @Test
    void 정보_수정_연락처_형식_위반_거부() throws Exception {
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contact", "연락주세요"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-info-invalid-contact"));
    }

    @Test
    void 정보_수정_SNS_200자_초과_거부() throws Exception {
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sns", "a".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-info-sns-too-long"));
    }

    @Test
    void 정보_수정_존재하지_않는_enum_값_거부() throws Exception {
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyType", "INVALID_TYPE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/update-info-invalid-enum-value"));
    }

    // ─── AddCompanyCareerRequest ──────────────────────────────────────

    @Test
    void 경력_추가_작품명_빈_문자열_거부() throws Exception {
        mockMvc.perform(post("/api/companies/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "", "startDate", "2024.01.01", "ongoing", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/add-career-blank-title"));
    }

    @Test
    void 경력_추가_작품명_100자_초과_거부() throws Exception {
        mockMvc.perform(post("/api/companies/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "A".repeat(101), "startDate", "2024.01.01", "ongoing", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/add-career-title-too-long"));
    }

    @Test
    void 경력_추가_시작일_미래날짜_거부() throws Exception {
        mockMvc.perform(post("/api/companies/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "작품명", "startDate", "2099.01.01", "ongoing", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/add-career-future-start-date"));
    }

    @Test
    void 경력_추가_시작일_누락_거부() throws Exception {
        mockMvc.perform(post("/api/companies/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workTitle", "작품명", "ongoing", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("company/validation/add-career-missing-start-date"));
    }
}
