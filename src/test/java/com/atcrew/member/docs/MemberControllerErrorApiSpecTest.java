package com.atcrew.member.docs;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.MemberService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MemberController가 던지는 MemberErrorCode 에러 응답을 실제로 재현해
 * REST Docs 스니펫과 openapi3 예시를 생성한다 (restdocs-api-spec 파이프라인).
 *
 * <p>PortfolioErrorApiSpecTest.java 패턴을 따른다 — 일반 document() 대신
 * MockMvcRestDocumentationWrapper.document(...) + ResourceDocumentation.resource(...)를 사용해야
 * resource.json이 생성되고 openapi3 태스크가 그 값을 읽는다.
 */
class MemberControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    MemberService memberService;

    @Test
    void 존재하지_않는_핸들로_조회시_404_MEMBER_NOT_FOUND() throws Exception {
        String nonExistentHandle = "no_such_handle_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        mockMvc.perform(get("/api/members/{handle}", nonExistentHandle))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andDo(document("member/get-by-handle-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 핸들로 회원 프로필 조회 시도 — 404 MEMBER_NOT_FOUND")));
    }

    @Test
    void 탈퇴한_회원이_보호된_API_호출시_403_MEMBER_DEACTIVATED() throws Exception {
        RegisteredMember member = registerMember("탈퇴테스트");

        // 본인 탈퇴 처리
        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNoContent());

        // 탈퇴 후 남은 Access Token으로 보호된 API 호출
        mockMvc.perform(patch("/api/members/me/name")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "새이름"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_DEACTIVATED"))
                .andDo(document("member/update-name-deactivated",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("탈퇴한 회원이 Access Token으로 이름 수정 시도 — 403 MEMBER_DEACTIVATED")));
    }

    @Test
    void 존재하지_않는_경력_삭제시_404_CAREER_NOT_FOUND() throws Exception {
        RegisteredMember member = registerMember("경력없음유저");
        String nonExistentCareerId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/members/me/careers/{careerId}", nonExistentCareerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAREER_NOT_FOUND"))
                .andDo(document("member/delete-career-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("본인 소유가 아니거나 존재하지 않는 경력 ID로 삭제 시도 — 404 CAREER_NOT_FOUND")));
    }

    @Test
    void 경력_50개_초과_등록시_400_CAREER_LIMIT_EXCEEDED() throws Exception {
        RegisteredMember member = registerMember("경력한도유저");

        // 한도(50개) 직전까지는 서비스 계층으로 직접 채워 HTTP 왕복 없이 빠르게 세팅한다.
        for (int i = 0; i < 50; i++) {
            memberService.addCareer(member.memberId(), new AddCareerCommand(
                    "참여작" + i, "작화", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), false, null));
        }

        Map<String, Object> body = Map.of(
                "workTitle", "51번째 참여작",
                "startDate", "2021.01.01",
                "endDate", "2021.12.31",
                "ongoing", false
        );

        mockMvc.perform(post("/api/members/me/careers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAREER_LIMIT_EXCEEDED"))
                .andDo(document("member/add-career-limit-exceeded",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("경력이 이미 50개인 회원이 경력을 추가 등록 시도 — 400 CAREER_LIMIT_EXCEEDED")));
    }

    @Test
    void 가용_슬롯이_전체_슬롯보다_클때_400_INVALID_SLOT_COUNT() throws Exception {
        RegisteredMember member = registerMember("슬롯초과유저");

        Map<String, Object> body = Map.of(
                "totalSlotCount", 1,
                "availableSlotCount", 5
        );

        mockMvc.perform(patch("/api/members/me/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLOT_COUNT"))
                .andDo(document("member/update-info-invalid-slot-count",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("가용 슬롯 수가 전체 슬롯 수보다 크게 프로필 수정 시도 — 400 INVALID_SLOT_COUNT")));
    }

    @Test
    void 경력_종료일_누락시_400_INVALID_CAREER_PERIOD() throws Exception {
        RegisteredMember member = registerMember("경력기간유저");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workTitle", "종료일없는작품");
        body.put("startDate", "2024.01.01");
        body.put("ongoing", false);

        mockMvc.perform(post("/api/members/me/careers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAREER_PERIOD"))
                .andDo(document("member/add-career-invalid-period",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("연재중이 아닌데 종료일 없이 경력 추가 시도 — 400 INVALID_CAREER_PERIOD")));
    }

    @Test
    void 유효하지_않은_시간대로_수정시_400_INVALID_TIMEZONE() throws Exception {
        RegisteredMember member = registerMember("시간대오류유저");

        mockMvc.perform(patch("/api/members/me/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("timezone", "Not/A_Zone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"))
                .andDo(document("member/update-info-invalid-timezone",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("IANA 시간대 카탈로그에 없는 값으로 프로필 수정 시도 — 400 INVALID_TIMEZONE")));
    }

    @Test
    void 유효하지_않은_국가코드로_수정시_400_INVALID_COUNTRY() throws Exception {
        RegisteredMember member = registerMember("국가코드오류유저");

        mockMvc.perform(patch("/api/members/me/info")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("countryCode", "ZZ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUNTRY"))
                .andDo(document("member/update-info-invalid-country",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("ISO 3166-1 alpha-2 형식이지만 실존하지 않는 국가 코드로 프로필 수정 시도 — 400 INVALID_COUNTRY")));
    }

    // ─── 회원가입 헬퍼 ────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰·회원 ID를 발급받는다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "member-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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
