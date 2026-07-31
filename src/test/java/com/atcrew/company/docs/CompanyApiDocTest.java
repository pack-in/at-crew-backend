package com.atcrew.company.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기업 프로필 API 문서화 통합 테스트.
 *
 * <p>전체 Spring 컨텍스트(MongoDB·MariaDB Testcontainer)를 기동해 기업 프로필 생성·조회·수정·경력 관리
 * API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class CompanyApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 기업_프로필_생성_및_조회_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("기업생성유저");

        MvcResult createResult = mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "앳크루스튜디오"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.recruitStatus").value("PREPARING"))
                .andExpect(jsonPath("$.data.isOwner").value(true))
                .andDo(document("company/create-company",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("companyName").description("기업명 (최대 16자)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("기업 프로필 ID"),
                                fieldWithPath("data.memberId").description("소유 회원 ID"),
                                fieldWithPath("data.companyName").description("기업명"),
                                fieldWithPath("data.recruitStatus").description("구인구직 상태 (기본 PREPARING)"),
                                fieldWithPath("data.hasBusinessRegistration").description("사업자 등록 여부(자기 신고)"),
                                fieldWithPath("data.activityFields").description("활동 분야 목록"),
                                fieldWithPath("data.activeRegions").description("활동 지역 목록"),
                                fieldWithPath("data.isOwner").description("조회자가 소유자인지 여부"),
                                fieldWithPath("data.createdAt").description("생성 일시 (ISO 8601)")
                        )
                ))
                .andReturn();
        String companyId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 비로그인 공개 조회 — isOwner=false
        mockMvc.perform(get("/api/companies/{companyId}", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isOwner").value(false))
                // 공개 중인 구인글이 없는 기업이므로 false (recruit 모듈 조회 결과)
                .andExpect(jsonPath("$.data.hasOpenJobPosting").value(false))
                .andDo(document("company/get-company",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("companyId").description("기업 프로필 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("기업 프로필 ID"),
                                fieldWithPath("data.companyName").description("기업명"),
                                fieldWithPath("data.recruitStatus").description("구인구직 상태"),
                                fieldWithPath("data.hasOpenJobPosting")
                                        .description("공개 중인 구인글 보유 여부 — 구인글 업로드 카드 진입점 판단용"),
                                fieldWithPath("data.isOwner").description("조회자가 소유자인지 여부 (비로그인이면 false)")
                        )
                ));

        // 본인 조회 — isOwner=true
        mockMvc.perform(get("/api/companies/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(companyId))
                .andExpect(jsonPath("$.data.isOwner").value(true))
                .andDo(document("company/get-my-company",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("기업 프로필 ID"),
                                fieldWithPath("data.companyName").description("기업명"),
                                fieldWithPath("data.isOwner").description("본인 조회이므로 항상 true")
                        )
                ));
    }

    @Test
    void 기업_프로필_중복_생성_409() throws Exception {
        String accessToken = registerMemberAndGetToken("중복생성유저");
        createCompany(accessToken, "첫번째기업");

        mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "두번째기업"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_ALREADY_EXISTS"))
                .andDo(document("company/create-company-duplicate", preprocessResponse(prettyPrint())));
    }

    @Test
    void 기업명_수정_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("기업명수정유저");
        createCompany(accessToken, "수정전기업");

        mockMvc.perform(patch("/api/companies/me/name")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", "수정된기업"))))
                .andExpect(status().isNoContent())
                .andDo(document("company/update-company-name",
                        preprocessRequest(prettyPrint()),
                        requestFields(
                                fieldWithPath("companyName").description("변경할 기업명 (최대 16자)")
                        )
                ));

        mockMvc.perform(get("/api/companies/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("수정된기업"));
    }

    @Test
    void 기업_정보_수정_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("정보수정유저");
        createCompany(accessToken, "정보수정기업");

        // null 필드는 변경하지 않는 부분 업데이트 — Map.of는 null 값을 허용하지 않아 LinkedHashMap 사용
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recruitStatus", "RECRUITING");
        body.put("companyType", "STUDIO");
        body.put("activityFields", List.of("WEBTOON", "ILLUSTRATION"));
        body.put("activeRegions", List.of("SEOUL"));
        body.put("contact", "010-1234-5678");
        body.put("sns", "https://instagram.com/atcrew");
        body.put("hasBusinessRegistration", true);

        mockMvc.perform(patch("/api/companies/me/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent())
                .andDo(document("company/update-company-info",
                        preprocessRequest(prettyPrint()),
                        requestFields(
                                fieldWithPath("recruitStatus").description("구인구직 상태 (PREPARING·RECRUITING·ALWAYS_RECRUITING·CLOSED). null이면 변경 없음").optional(),
                                fieldWithPath("companyType").description("회사 형태 (STUDIO·PLATFORM·AGENCY·PUBLISHER·INDIVIDUAL_STUDIO·SMALL_TEAM·OTHER). null이면 변경 없음").optional(),
                                fieldWithPath("activityFields").description("활동 분야 (최대 5개). null이면 변경 없음, []이면 전체 삭제").optional(),
                                fieldWithPath("activeRegions").description("활동 지역 (최대 7개). null이면 변경 없음, []이면 전체 삭제").optional(),
                                fieldWithPath("contact").description("연락처 (전화번호 또는 이메일)").optional(),
                                fieldWithPath("sns").description("SNS 링크 (최대 200자)").optional(),
                                fieldWithPath("hasBusinessRegistration").description("사업자 등록 여부(자기 신고)").optional()
                        )
                ));

        mockMvc.perform(get("/api/companies/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruitStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.companyType").value("STUDIO"))
                .andExpect(jsonPath("$.data.contact").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.hasBusinessRegistration").value(true))
                .andExpect(jsonPath("$.data.activityFields.length()").value(2))
                .andExpect(jsonPath("$.data.activeRegions[0]").value("SEOUL"));
    }

    @Test
    void 경력_추가_및_목록_조회_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("경력추가유저");
        String companyId = createCompany(accessToken, "경력기업");

        Map<String, Object> career = new LinkedHashMap<>();
        career.put("workTitle", "홍길동전");
        career.put("startDate", "2024.01.01");
        career.put("endDate", null);
        career.put("ongoing", true);
        career.put("description", "네이버웹툰 연재");

        mockMvc.perform(post("/api/companies/me/careers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(career)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workTitle").value("홍길동전"))
                .andExpect(jsonPath("$.data.ongoing").value(true))
                .andDo(document("company/add-company-career",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("workTitle").description("작품 이름 (최대 100자)"),
                                fieldWithPath("startDate").description("작업 시작일 (yyyy.MM.dd)"),
                                fieldWithPath("endDate").description("작업 종료일 (연재중이면 null)").optional(),
                                fieldWithPath("ongoing").description("연재중 여부"),
                                fieldWithPath("description").description("작품 관련 링크나 설명 (최대 200자)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("경력 ID"),
                                fieldWithPath("data.workTitle").description("작품 이름"),
                                fieldWithPath("data.startDate").description("작업 시작일 (yyyy.MM.dd)"),
                                fieldWithPath("data.ongoing").description("연재중 여부"),
                                fieldWithPath("data.description").description("작품 관련 링크나 설명").optional()
                        )
                ));

        // 경력 목록은 인증 없이 조회 가능
        mockMvc.perform(get("/api/companies/{companyId}/careers", companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andDo(document("company/list-company-careers",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("companyId").description("기업 프로필 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data[].id").description("경력 ID"),
                                fieldWithPath("data[].workTitle").description("작품 이름"),
                                fieldWithPath("data[].startDate").description("작업 시작일 (yyyy.MM.dd)"),
                                fieldWithPath("data[].ongoing").description("연재중 여부")
                        )
                ));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "company-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 기업 프로필을 생성하고 companyId를 반환한다. */
    private String createCompany(String accessToken, String companyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("companyName", companyName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
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
