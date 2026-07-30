package com.atcrew;

import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 보안 필터 체인 통합 테스트.
 *
 * <p>인증/인가 경계를 전체 스택(MockMvc)으로 검증한다.
 * 대표 케이스(이름수정_토큰_없음_401)에만 REST Docs 스니펫을 생성한다.
 */
class SecurityIntegrationTest extends RestDocsIntegrationSupport {

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    MemberService memberService;

    // ─── 섹션 1: 보호된 엔드포인트 토큰 없음 → 401 ──────────────────────

    @Test
    void 이름수정_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                // 보안 필터 401 응답 스니펫 생성 (대표 케이스)
                .andDo(document("security/unauthenticated"));
    }

    @Test
    void 프로필수정_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 경력추가_토큰_없음_401() throws Exception {
        mockMvc.perform(post("/api/members/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 경력삭제_토큰_없음_401() throws Exception {
        mockMvc.perform(delete("/api/members/me/careers/some-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 탈퇴_토큰_없음_401() throws Exception {
        mockMvc.perform(delete("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 기업_프로필_생성_토큰_없음_401() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"앳크루스튜디오\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 내_기업_프로필_조회_토큰_없음_401() throws Exception {
        mockMvc.perform(get("/api/companies/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 기업명_수정_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/companies/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"앳크루스튜디오\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 기업_정보_수정_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/companies/me/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 기업_경력_추가_토큰_없음_401() throws Exception {
        mockMvc.perform(post("/api/companies/me/careers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ─── 섹션 2: 유효하지 않은 JWT → 401 ─────────────────────────────

    @Test
    void 형식_위반_JWT_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage.jwt.value")
                        .content("{\"name\":\"홍길동\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ─── 섹션 3: Refresh 토큰을 Access 토큰으로 오용 → 401 ──────────

    @Test
    void refresh_토큰_Authorization_헤더_사용_401() throws Exception {
        String refresh = jwtProvider.generateRefreshToken("test-member-id");
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh)
                        .content("{\"name\":\"홍길동\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ─── 섹션 4: 유효한 Access 토큰 → 인증 통과 ─────────────────────

    @Test
    void 유효한_토큰_이름수정_204() throws Exception {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "테스트유저", CreatorRole.WEBTOON);
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 유효한_토큰_탈퇴_204() throws Exception {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "탈퇴유저", CreatorRole.OTHER);
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ─── 섹션 5: 공개 엔드포인트 → 토큰 없이 접근 가능 ─────────────

    @Test
    void 핸들_조회_토큰_없이_401_아님() throws Exception {
        // 없는 핸들이어도 보안 필터를 통과 → 404
        mockMvc.perform(get("/api/members/nonexistent_handle"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 이메일_로그인_엔드포인트_토큰_없이_401_아님() throws Exception {
        // 빈 바디 → 400 (입력 검증 실패), 401이 아님을 확인
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void Google_로그인_엔드포인트_토큰_없이_401_아님() throws Exception {
        // 빈 바디 → 400 (입력 검증 실패), 401이 아님을 확인
        mockMvc.perform(post("/api/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 기업_프로필_공개_조회_토큰_없이_401_아님() throws Exception {
        // 없는 기업 ID여도 보안 필터를 통과 → 404
        mockMvc.perform(get("/api/companies/{companyId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 기업_경력_목록_토큰_없이_401_아님() throws Exception {
        mockMvc.perform(get("/api/companies/{companyId}/careers", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 헬스체크_토큰_없이_200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void 검색_토큰_없이_401_아님() throws Exception {
        // 검색어·필터 없음 → 최초 진입 상태로 빈 결과, 401이 아님을 확인
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return "sec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@test.com";
    }

    private String uniqueHandle() {
        return "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
