package com.atcrew.recruit.docs;

import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관심 작가 API 문서화 통합 테스트 (docs/design/recruit-module-design.md §2.7, §4.3).
 *
 * <p>기업 계정 전용 기능이지만 기업 인증 게이팅은 아직 스텁이므로, 인증된 회원 2명(기업 역할·작가 역할)을
 * 회원가입으로 준비해 관심 작가 저장/해제, 최근 본 작가 기록/조회 흐름을 문서화한다.
 */
class LikedArtistApiDocTest extends RestDocsIntegrationSupport {

    @Test
    void 관심작가_저장_목록조회_해제_문서화() throws Exception {
        String companyToken = registerMemberAndGetToken("관심작가기업");
        String artistMemberId = registerMemberAndGetMemberId("관심작가대상작가");

        mockMvc.perform(post("/api/recruit/liked-artists/{artistMemberId}", artistMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/like-artist",
                        pathParameters(
                                parameterWithName("artistMemberId").description("저장할 작가 Member ID")
                        )
                ));

        mockMvc.perform(get("/api/recruit/liked-artists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].artistMemberId").value(artistMemberId))
                .andDo(document("recruit/list-liked-artists",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("q").description("검색어 — 작가 이름·핸들 부분 일치").optional(),
                                parameterWithName("cursor").description("커서").optional(),
                                parameterWithName("size").description("페이지 크기 (기본 20, 최대 50)").optional()
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].artistMemberId").description("작가 Member ID"),
                                fieldWithPath("data.items[].artistName").description("작가 표시명").optional(),
                                fieldWithPath("data.items[].likedAt").description("좋아요 저장 시각 (ISO 8601)"),
                                fieldWithPath("data.nextCursor").type(JsonFieldType.STRING)
                                        .description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        // 검색어 필터 — 저장한 작가 중 이름이 일치하는 작가만 조회된다(설계 §2.7)
        mockMvc.perform(get("/api/recruit/liked-artists")
                        .param("q", "관심작가대상")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].artistMemberId").value(artistMemberId));

        mockMvc.perform(get("/api/recruit/liked-artists")
                        .param("q", "일치하지않는이름")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(delete("/api/recruit/liked-artists/{artistMemberId}", artistMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/unlike-artist",
                        pathParameters(
                                parameterWithName("artistMemberId").description("해제할 작가 Member ID")
                        )
                ));

        mockMvc.perform(get("/api/recruit/liked-artists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void 작가_조회기록_및_최근본작가_목록_문서화() throws Exception {
        String companyToken = registerMemberAndGetToken("최근조회기업");
        String artistMemberId = registerMemberAndGetMemberId("최근조회대상작가");

        mockMvc.perform(post("/api/recruit/recently-viewed-artists/{artistMemberId}", artistMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isNoContent())
                .andDo(document("recruit/record-artist-view",
                        pathParameters(
                                parameterWithName("artistMemberId").description("조회 기록할 작가 Member ID")
                        )
                ));

        mockMvc.perform(get("/api/recruit/recently-viewed-artists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].artistMemberId").value(artistMemberId))
                .andDo(document("recruit/list-recently-viewed-artists",
                        preprocessResponse(prettyPrint()),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.items[].artistMemberId").description("작가 Member ID"),
                                fieldWithPath("data.items[].artistName").description("작가 표시명").optional(),
                                fieldWithPath("data.items[].viewedAt").description("마지막 조회 시각 (ISO 8601)"),
                                fieldWithPath("data.nextCursor").type(JsonFieldType.STRING)
                                        .description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("data.hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    /** 이메일 회원가입으로 액세스 토큰을 발급받는다. */
    private String registerMemberAndGetToken(String name) throws Exception {
        MvcResult result = registerMember(name);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    /** 이메일 회원가입으로 회원 ID를 발급받는다. */
    private String registerMemberAndGetMemberId(String name) throws Exception {
        MvcResult result = registerMember(name);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/member/id").asText();
    }

    private MvcResult registerMember(String name) throws Exception {
        String uniqueEmail = "liked-artist-doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
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
