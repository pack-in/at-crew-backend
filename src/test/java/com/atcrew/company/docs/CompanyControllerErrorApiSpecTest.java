package com.atcrew.company.docs;

import com.atcrew.company.AddCompanyCareerCommand;
import com.atcrew.company.CompanyService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CompanyController가 던지는 CompanyErrorCode 에러 응답을 실제로 재현해
 * REST Docs 스니펫과 openapi3 예시를 생성한다 (restdocs-api-spec 파이프라인).
 *
 * <p>PortfolioErrorApiSpecTest.java / MemberControllerErrorApiSpecTest.java 패턴을 따른다 —
 * 일반 document() 대신 MockMvcRestDocumentationWrapper.document(...) +
 * ResourceDocumentation.resource(...)를 사용해야 resource.json이 생성되고 openapi3 태스크가
 * 그 값을 읽는다.
 *
 * <p>CompanyErrorCode 6개 중 공개 API로 실제 트리거 가능한 4개만 커버한다. 나머지 2개는 건너뛴다.
 * <ul>
 *   <li>COMPANY_ACCESS_DENIED — {@code Company.assertOwner}는 {@code loadOwnedCompany}에서만
 *       호출되는데, 이 메서드가 이미 {@code companyRepository.findByMemberId(memberId)}로 조회한
 *       회사를 같은 {@code memberId}로 재검증하는 구조라 결과가 항상 일치한다. 공개 API 경로로는
 *       다른 회원의 memberId를 주입할 방법이 없어 트리거 불가능하다.
 *   <li>CAREER_NOT_FOUND — CompanyErrorCode에 정의만 되어 있고 코드베이스 전체에서 이 코드를
 *       던지는 지점이 없다(경력 삭제·수정 API 자체가 company 모듈에 없음). 죽은 코드.
 * </ul>
 */
class CompanyControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    CompanyService companyService;

    @Test
    void 존재하지_않는_기업으로_조회시_404_COMPANY_NOT_FOUND() throws Exception {
        String nonExistentCompanyId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/companies/{companyId}", nonExistentCompanyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"))
                .andDo(document("company/get-company-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 기업 프로필 ID로 조회 시도 — 404 COMPANY_NOT_FOUND")));
    }

    @Test
    void 기업_프로필_중복_생성시_409_COMPANY_ALREADY_EXISTS() throws Exception {
        RegisteredMember member = registerMember("중복생성에러스펙유저");
        createCompany(member.accessToken(), "첫번째기업");

        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "두번째기업"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_EXISTS"))
                .andDo(document("company/create-company-already-exists",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이미 기업 프로필을 보유한 회원이 재생성 시도 — 409 COMPANY_ALREADY_EXISTS")));
    }

    @Test
    void 경력_50개_초과_등록시_400_CAREER_LIMIT_EXCEEDED() throws Exception {
        RegisteredMember member = registerMember("경력한도에러스펙유저");
        createCompany(member.accessToken(), "경력한도기업");

        // 한도(50개) 직전까지는 서비스 계층으로 직접 채워 HTTP 왕복 없이 빠르게 세팅한다.
        for (int i = 0; i < 50; i++) {
            companyService.addCareer(member.memberId(), new AddCompanyCareerCommand(
                    "참여작" + i, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), false, null));
        }

        Map<String, Object> body = Map.of(
                "workTitle", "51번째 참여작",
                "startDate", "2021.01.01",
                "endDate", "2021.12.31",
                "ongoing", false
        );

        mockMvc.perform(post("/api/companies/me/careers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAREER_LIMIT_EXCEEDED"))
                .andDo(document("company/add-career-limit-exceeded",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("경력이 이미 50개인 기업이 경력을 추가 등록 시도 — 400 CAREER_LIMIT_EXCEEDED")));
    }

    @Test
    void 경력_종료일_누락시_400_INVALID_CAREER_PERIOD() throws Exception {
        RegisteredMember member = registerMember("경력기간에러스펙유저");
        createCompany(member.accessToken(), "경력기간기업");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workTitle", "종료일없는작품");
        body.put("startDate", "2024.01.01");
        body.put("ongoing", false);

        mockMvc.perform(post("/api/companies/me/careers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAREER_PERIOD"))
                .andDo(document("company/add-career-invalid-period",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("연재중이 아닌데 종료일 없이 경력 추가 시도 — 400 INVALID_CAREER_PERIOD")));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "company-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new RegisteredMember(
                objectMapper.readTree(body).at("/data/accessToken").asText(),
                objectMapper.readTree(body).at("/data/member/id").asText());
    }

    /** 기업 프로필을 생성한다. */
    private void createCompany(String accessToken, String companyName) throws Exception {
        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", companyName))))
                .andExpect(status().isCreated());
    }

    private record RegisteredMember(String accessToken, String memberId) {
    }

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
