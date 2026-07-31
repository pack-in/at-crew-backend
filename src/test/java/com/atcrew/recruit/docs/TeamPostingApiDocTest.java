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

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 팀원모집글 API 문서화 통합 테스트.
 *
 * <p>전체 Spring 컨텍스트(MongoDB·MariaDB Testcontainer)를 기동해 팀원모집글 작성·수정·조회·상태전이·
 * 끌어올리기·휴지통 API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 * TeamPosting은 JobPosting과 달리 승인 절차가 없어 생성 즉시 PUBLISHED로 게시된다(설계 §4.2).
 */
class TeamPostingApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 팀원모집글_전체_생명주기_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("팀모집작성자");

        // 1. 작성 — 승인 절차 없이 즉시 PUBLISHED
        MvcResult createResult = mockMvc.perform(post("/api/recruit/team-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullCreateBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andDo(document("recruit/create-team-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("모집글 제목 (최대 200자)"),
                                fieldWithPath("isBusinessRegistered").description("사업자등록 여부"),
                                fieldWithPath("isResumeRequired").description("이력서 필수 여부"),
                                fieldWithPath("isCoverLetterRequired").description("자기소개서 필수 여부"),
                                fieldWithPath("authorName").description("모집자(팀/개인) 표시명 (최대 100자)").optional(),
                                fieldWithPath("contact").description("연락처 (최대 100자)").optional(),
                                fieldWithPath("authorDescription").description("모집자 소개 (최대 5000자)").optional(),
                                fieldWithPath("recruitPurposes").description("모집 목적 (최대 20개, 표시 전용)").optional(),
                                fieldWithPath("workLocationType").description("활동 형태 (OFFLINE·ONLINE·HYBRID)").optional(),
                                fieldWithPath("activityRegion").description("활동 지역 (ONLINE이면 null이어야 함, 최대 200자)").optional(),
                                fieldWithPath("roles").description("모집 역할 (최대 20개)").optional(),
                                fieldWithPath("genres").description("모집 장르 (최대 20개)").optional(),
                                fieldWithPath("hasParticipationFee").description("참여비용 존재 여부"),
                                fieldWithPath("hasProfitSharing").description("수익배분 존재 여부"),
                                fieldWithPath("deadline").description("마감일 (yyyy-MM-dd, 오늘 이후. null이면 상시모집)").optional(),
                                fieldWithPath("recruitCount").description("모집 인원 (1~9999)").optional(),
                                fieldWithPath("selectionProcess").description("선발 절차 (최대 5000자)").optional(),
                                fieldWithPath("activityDuration").description("예상 활동 기간 (ONE_MONTH·THREE_MONTHS·SIX_MONTHS·ONE_YEAR_PLUS)").optional(),
                                fieldWithPath("weeklyActivityTime").description("주당 활동 시간 (FLEXIBLE·ONCE_A_WEEK·TWO_TO_THREE_TIMES·FIVE_PLUS_TIMES)").optional(),
                                fieldWithPath("projectDescription").description("프로젝트 소개 (최대 5000자)").optional(),
                                fieldWithPath("thumbnailImage").description("썸네일 이미지 URL (최대 500자)").optional(),
                                fieldWithPath("referenceImages").description("참고 이미지 URL 목록 (최대 20개, 표시 전용)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.authorMemberId").description("작성자 Member ID"),
                                fieldWithPath("data.title").description("모집글 제목"),
                                fieldWithPath("data.status").description("상태 (DRAFT·PUBLISHED·CLOSED·DELETED, 생성 즉시 PUBLISHED)"),
                                fieldWithPath("data.bookmarkCount").description("북마크 수"),
                                fieldWithPath("data.viewCount").description("조회 수"),
                                fieldWithPath("data.boostedUntil").description("끌어올리기 만료 시각 (null이면 상단고정 아님)"),
                                fieldWithPath("data.createdAt").description("생성 시각 (ISO 8601)")
                        )
                ))
                .andReturn();
        String teamPostingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 2. 수정 — 작성자 본인만 가능, 부분 업데이트(null 필드는 기존 값 유지)
        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("title", "웹툰 팀원 모집 (수정)");
        updateBody.put("recruitCount", 5);
        updateBody.put("selectionProcess", "포트폴리오 심사 → 화상 면접");

        mockMvc.perform(put("/api/recruit/team-postings/{teamPostingId}", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("웹툰 팀원 모집 (수정)"))
                .andExpect(jsonPath("$.data.recruitCount").value(5))
                .andDo(document("recruit/update-team-posting",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("title").description("변경할 제목. null이면 변경 없음").optional(),
                                fieldWithPath("recruitCount").description("변경할 모집 인원. null이면 변경 없음").optional(),
                                fieldWithPath("selectionProcess").description("변경할 선발 절차. null이면 변경 없음").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.title").description("변경된 제목"),
                                fieldWithPath("data.recruitCount").description("변경된 모집 인원")
                        )
                ));

        // 3. 상세 조회 — 인증 불필요, PUBLISHED는 누구나 조회 가능
        mockMvc.perform(get("/api/recruit/team-postings/{teamPostingId}", teamPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("recruit/get-team-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("teamPostingId").description("팀원모집글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.title").description("모집글 제목"),
                                fieldWithPath("data.status").description("상태"),
                                fieldWithPath("data.viewCount").description("조회 수 (조회할 때마다 증가, 작성자 본인 조회는 제외)")
                        )
                ));

        // 4. 목록(커서) — PUBLISHED만, 인증 불필요
        mockMvc.perform(get("/api/recruit/team-postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("recruit/list-team-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("팀원모집글 ID"),
                                fieldWithPath("data.items[].title").description("모집글 제목"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (없으면 null)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 5. 내 목록 — DELETED를 제외한 모든 상태
        mockMvc.perform(get("/api/recruit/team-postings/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(teamPostingId))
                .andDo(document("recruit/list-my-team-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("팀원모집글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (DELETED 제외)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 6. 끌어올리기 — 48시간 동안 목록 상단 고정
        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/boost", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boostedUntil").isNotEmpty())
                .andDo(document("recruit/boost-team-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("teamPostingId").description("팀원모집글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.boostedUntil").description("끌어올리기 만료 시각 (적용 시점 + 48시간)")
                        )
                ));

        // 7. 상태 변경(마감) — 현재는 CLOSED 전이만 지원
        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/status", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andDo(document("recruit/update-team-posting-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("status").description("변경할 상태 (현재는 CLOSED만 허용)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.status").description("변경된 상태 (CLOSED)")
                        )
                ));

        // 8. 삭제(휴지통 이동)
        mockMvc.perform(delete("/api/recruit/team-postings/{teamPostingId}", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/delete-team-posting",
                        pathParameters(
                                parameterWithName("teamPostingId").description("팀원모집글 ID")
                        )
                ));

        // 9. 휴지통 목록
        mockMvc.perform(get("/api/recruit/team-postings/trash")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(teamPostingId))
                .andExpect(jsonPath("$.data.items[0].status").value("DELETED"))
                .andDo(document("recruit/list-trashed-team-postings",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].id").description("팀원모집글 ID"),
                                fieldWithPath("data.items[].status").description("상태 (DELETED)"),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 10. 복구 — 승인 절차가 없으므로 즉시 PUBLISHED로 재게시
        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/restore", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andDo(document("recruit/restore-team-posting",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("teamPostingId").description("팀원모집글 ID")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.id").description("팀원모집글 ID"),
                                fieldWithPath("data.status").description("변경된 상태 (PUBLISHED)")
                        )
                ));
    }

    @Test
    void 작성자가_아니면_수정할_수_없다_403_문서화() throws Exception {
        String authorToken = registerMemberAndGetToken("팀모집소유자");
        String strangerToken = registerMemberAndGetToken("팀모집타인");
        String teamPostingId = createTeamPosting(authorToken);

        mockMvc.perform(put("/api/recruit/team-postings/{teamPostingId}", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "타인이 수정 시도"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_AUTHOR"))
                .andDo(document("recruit/update-team-posting-forbidden",
                        preprocessResponse(prettyPrint())));
    }

    @Test
    void 이미_끌어올리기_적용_중이면_409로_거부된다_문서화() throws Exception {
        String accessToken = registerMemberAndGetToken("팀모집부스터");
        String teamPostingId = createTeamPosting(accessToken);

        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/boost", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruit/team-postings/{teamPostingId}/boost", teamPostingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOST_COOLDOWN"))
                .andDo(document("recruit/boost-team-posting-cooldown",
                        preprocessResponse(prettyPrint())));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        String uniqueEmail = "team-posting-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    /** 팀원모집글을 생성하고 teamPostingId를 반환한다. */
    private String createTeamPosting(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recruit/team-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullCreateBody())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
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
