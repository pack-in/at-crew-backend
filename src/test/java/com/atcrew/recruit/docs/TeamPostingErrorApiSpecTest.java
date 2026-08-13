package com.atcrew.recruit.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
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
 * 팀원모집글(TeamPosting) API 에러 응답 문서화 테스트.
 *
 * <p>{@code MockMvcRestDocumentationWrapper.document()} + {@code ResourceDocumentation.resource()}
 * 조합을 사용해 openapi3 태스크가 읽을 수 있는 resource.json을 생성한다(restdocs-api-spec 파이프라인).
 * TEAM_POSTING_NOT_FOUND, INVALID_ACTIVITY_REGION은 이 컨트롤러 경로가 가장 간단한 대표 트리거다
 * (FORBIDDEN_NOT_AUTHOR·BOOST_COOLDOWN은 JobPostingErrorApiSpecTest에서 대표로 커버한다).
 */
class TeamPostingErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Test
    void 존재하지_않는_팀원모집글_조회시_404_TEAM_POSTING_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/recruit/team-postings/{teamPostingId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_POSTING_NOT_FOUND"))
                .andDo(document("recruit/get-team-posting-not-found",
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않는 팀원모집글 ID로 상세 조회 — 404 TEAM_POSTING_NOT_FOUND")));
    }

    @Test
    void 온라인_활동에_활동지역_입력시_400_INVALID_ACTIVITY_REGION() throws Exception {
        String authorToken = registerMemberAndGetToken("활동지역위반작성자");

        Map<String, Object> body = fullCreateBody();
        body.put("workLocationType", "ONLINE");
        body.put("activityRegion", "서울");

        mockMvc.perform(post("/api/recruit/team-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVITY_REGION"))
                .andDo(document("recruit/create-team-posting-invalid-activity-region",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("온라인 활동인데 활동 지역을 입력한 팀원모집글 작성 시도 — 400 INVALID_ACTIVITY_REGION")));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "team-posting-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    /** CreateTeamPostingRequest의 모든 필드를 채운 요청 바디를 반환한다. */
    private Map<String, Object> fullCreateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "웹툰 팀원 모집");
        body.put("isBusinessRegistered", false);
        body.put("isResumeRequired", true);
        body.put("isCoverLetterRequired", false);
        body.put("authorName", "홍길동팀");
        body.put("contact", "010-1234-5678");
        body.put("authorDescription", "액션 판타지 웹툰 팀입니다");
        body.put("recruitPurposes", List.of("공모전 준비"));
        body.put("workLocationType", "OFFLINE");
        body.put("activityRegion", "서울");
        body.put("roles", List.of("배경", "채색"));
        body.put("genres", List.of("액션", "판타지"));
        body.put("hasParticipationFee", false);
        body.put("hasProfitSharing", true);
        body.put("deadline", "2099-12-31");
        body.put("recruitCount", 3);
        body.put("selectionProcess", "포트폴리오 심사 후 면접");
        body.put("activityDuration", "THREE_MONTHS");
        body.put("weeklyActivityTime", "TWO_TO_THREE_TIMES");
        body.put("projectDescription", "판타지 액션 웹툰 프로젝트");
        body.put("thumbnailImage", "https://img.example/team-thumb.png");
        body.put("referenceImages", List.of("https://img.example/ref1.png"));
        return body;
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
