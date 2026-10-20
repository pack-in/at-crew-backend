package com.atcrew;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.UploadArtworkCommand;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.media.internal.application.MediaKeySigner;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.portfolio.PortfolioInfo;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.application.PortfolioServiceImpl;
import com.atcrew.support.BillingTestSupport;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
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

    // 공개 주소 응답 검증용 픽스처 — 작품·포트폴리오는 서비스로 직접 만든다(PortfolioApiDocTest와 같은 방식).
    @Autowired
    ArtworkService artworkService;

    @Autowired
    PortfolioServiceImpl portfolioService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    // 업로드 key의 소유자 서명(#190) — 테스트도 같은 규칙으로 key를 만든다.
    @Autowired
    MediaKeySigner keySigner;

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
    void handle_변경_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"Pack_1n\"}"))
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
    void 마케팅_동의_변경_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/marketing-agreement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 성인_콘텐츠_설정_변경_토큰_없음_401() throws Exception {
        mockMvc.perform(patch("/api/members/me/adult-content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 로그아웃_토큰_없음_401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some.refresh.token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 비밀번호_변경_토큰_없음_401() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secure1!\",\"newPassword\":\"Changed1!\",\"newPasswordConfirm\":\"Changed1!\"}"))
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
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "테스트유저");
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 유효한_토큰_handle_변경_후_내_정보에_다음_변경_가능_시각이_담긴다() throws Exception {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "핸들변경");
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());
        String newHandle = "Sec_" + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(patch("/api/members/me/handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content("{\"handle\":\"" + newHandle + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(newHandle))
                .andExpect(jsonPath("$.data.handleChangeAvailableAt").isNotEmpty());

        mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle").value(newHandle))
                .andExpect(jsonPath("$.data.handleChangeAvailableAt").isNotEmpty());

        // 토큰이 있으면 본인 소유 handle은 사용 가능으로 판정한다
        mockMvc.perform(get("/api/members/handles/availability").param("handle", newHandle.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(get("/api/members/handles/availability").param("handle", newHandle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.errorCode").value("DUPLICATE_HANDLE"));
    }

    @Test
    void 유효한_토큰_탈퇴_204() throws Exception {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), "탈퇴유저");
        String token = jwtProvider.generateAccessToken(member.id(), member.loginEmail());

        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ─── 섹션 4-1: 관리자 전용 경로 → 로그인한 일반 회원도 403 ─────────────
    // 관리자 권한 체계가 없어 누구에게도 열지 않는다(SecurityConfig). 전에는 로그인만 하면 호출됐다.
    // 본문 code까지 본다 — 기본 처리기(sendError)는 본문이 비고, 실제 서버에서는 /error 재디스패치로 401이 된다.

    @Test
    void 관리자_전용_배너_쓰기는_로그인해도_403() throws Exception {
        String token = memberToken("배너차단");

        mockMvc.perform(post("/api/community/banners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"x\",\"imageUrl\":\"https://a.com/b.png\",\"linkUrl\":\"https://a.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/api/community/banners/{bannerId}", "some-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/community/banners/{bannerId}", "some-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자_전용_구인글_승인_경로는_로그인해도_403() throws Exception {
        String token = memberToken("승인차단");

        mockMvc.perform(get("/api/recruit/admin/job-postings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/api/recruit/admin/job-postings/{id}/approve", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/api/recruit/admin/job-postings/{id}/reject", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자_전용_경로는_토큰이_없으면_401() throws Exception {
        mockMvc.perform(delete("/api/community/banners/{bannerId}", "some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 배너_목록_조회는_계속_공개다() throws Exception {
        mockMvc.perform(get("/api/community/banners"))
                .andExpect(status().isOk());
    }

    private String memberToken(String name) {
        MemberInfo member = memberService.register(uniqueEmail(), uniqueHandle(), name);
        return jwtProvider.generateAccessToken(member.id(), member.loginEmail());
    }

    // ─── 섹션 5: 공개 엔드포인트 → 토큰 없이 접근 가능 ─────────────

    @Test
    void 핸들_조회_토큰_없이_401_아님() throws Exception {
        // 없는 핸들이어도 보안 필터를 통과 → 404
        mockMvc.perform(get("/api/members/nonexistent_handle"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handle_사전_확인_토큰_없이_200() throws Exception {
        // 인증 선택 엔드포인트 — 가입 화면은 토큰 없이 호출한다(member-handle-design §1.4)
        mockMvc.perform(get("/api/members/handles/availability").param("handle", "Free_handle1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
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
    void 재색인_artwork_permitAll_통과() throws Exception {
        // 시크릿 헤더가 틀려도 Security 필터는 통과해야 한다 — 401이 나더라도 code가
        // UNAUTHENTICATED(필터 차단)가 아니라 INTERNAL_SECRET_INVALID(컨트롤러 도달)여야 통과로 본다.
        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SECRET_INVALID"));
    }

    // OG 콜백·백필 API — Security 필터를 통과해 컨트롤러의 시크릿 검사에 도달해야 한다(필터 차단 코드가 아니어야 한다).
    @Test
    void og_내부_API_permitAll_통과() throws Exception {
        mockMvc.perform(post("/internal/media/images/og-processed")
                        .header("X-Internal-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerType\":\"ARTWORK_THUMBNAIL\",\"ownerId\":\"a\",\"imageKey\":\"raw/a.png\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("UNAUTHENTICATED")));
        mockMvc.perform(post("/internal/media/og/backfill").header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("UNAUTHENTICATED")));
        mockMvc.perform(post("/internal/portfolio/og/backfill-snapshots").header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("UNAUTHENTICATED")));
    }

    @Disabled("MVP 범위 밖 — recruit(구인·구직·팀원모집) 미출시, 출시 시 해제")
    @Test
    void 재색인_recruit_permitAll_통과() throws Exception {
        // 이슈 #114 회귀 테스트 — SecurityConfig의 permitAll 목록에서 이 경로만 빠져 있어서
        // 항상 UNAUTHENTICATED(필터 차단)로 막혔었다.
        mockMvc.perform(post("/internal/search/reindex/recruit")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SECRET_INVALID"));
    }

    @Test
    void 작품_열람_기록_토큰_없이_401_아님() throws Exception {
        // 없는 작품이어도 기록 여부를 응답으로 구분하지 않는다 → 204
        mockMvc.perform(post("/api/artworks/{artworkId}/views", UUID.randomUUID().toString())
                        .header("X-Anonymous-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 핫_작품_토큰_없이_401_아님() throws Exception {
        mockMvc.perform(get("/api/community/artworks/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void 검색_토큰_없이_401_아님() throws Exception {
        // 검색어·필터 없음 → 최초 진입 상태로 빈 결과, 401이 아님을 확인
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    // ─── 공개 주소 /{handle}/artwork|portfolio/{code} (member-handle-design §2.2) ─────

    // /api/members/me/**는 {handle} 하위 공개 패턴보다 먼저 인증으로 막힌다 — 매핑이 없는 경로도 401이 먼저다.
    @Test
    void 본인_전용_하위_경로는_공개_패턴에_걸리지_않고_토큰_없으면_401() throws Exception {
        for (String path : new String[] {
                "/api/members/me/artworks/abcdef",
                "/api/members/me/portfolios/abcdef",
                "/api/members/me/portfolios/abcdef/artworks",
                "/api/members/me/portfolios/abcdef/snapshots/" + UUID.randomUUID()}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    // permitAll이 실제로 열려 있는지 — 인증 없이 서비스까지 도달해 handle 해석 결과(404)가 나와야 한다.
    @Test
    void 공개_주소는_토큰_없이_handle_해석까지_도달한다() throws Exception {
        String unknown = uniqueHandle();
        for (String path : new String[] {
                "/api/members/" + unknown + "/artworks/abcdef",
                "/api/members/" + unknown + "/portfolios/abcdef",
                "/api/members/" + unknown + "/portfolios/abcdef/artworks",
                "/api/members/" + unknown + "/portfolios/abcdef/snapshots/" + UUID.randomUUID()}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        }
    }

    @Test
    void 공개_주소_경로_변수_형식이_틀리면_400() throws Exception {
        String tooLongHandle = "a".repeat(51);
        for (String path : new String[] {
                "/api/members/valid_handle/artworks/ABCDEF",
                "/api/members/valid_handle/artworks/abcde",
                "/api/members/" + tooLongHandle + "/artworks/abcdef",
                "/api/members/valid_handle/portfolios/abc12!",
                "/api/members/" + tooLongHandle + "/portfolios/abcdef/artworks",
                "/api/members/valid_handle/portfolios/abcdef/snapshots/not-a-uuid"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        }
    }

    @Test
    void 공개_주소로_비로그인_포트폴리오_열람_200과_주소_조립_정보() throws Exception {
        MemberInfo owner = memberService.register(uniqueEmail(), uniqueHandle(), "공개주소");
        BillingTestSupport.grantProPlan(subscriptionRepository, owner.id());
        PortfolioInfo created = portfolioService.createShared(owner.id(), "공유", ReflectionType.LIVE, List.of());

        mockMvc.perform(get("/api/members/{handle}/portfolios/{code}", owner.handle(), created.publicCode()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.data.id").value(created.id()))
                .andExpect(jsonPath("$.data.ownerHandle").value(owner.handle()))
                .andExpect(jsonPath("$.data.publicCode").value(created.publicCode()));
        mockMvc.perform(get("/api/members/{handle}/portfolios/{code}/artworks", owner.handle(), created.publicCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void 공개_주소와_ID_작품_상세_응답에_현재_handle과_코드가_담긴다() throws Exception {
        MemberInfo author = memberService.register(uniqueEmail(), uniqueHandle(), "공개작품");
        String token = jwtProvider.generateAccessToken(author.id(), author.loginEmail());
        String name = UUID.randomUUID().toString();
        String key = "raw/" + keySigner.sign(author.id(), name) + "/" + name + ".png";
        ArtworkInfo uploaded = artworkService.uploadArtwork(author.id(), new UploadArtworkCommand(
                List.of(key), 0, null, ImageLayoutType.VERTICAL_SCROLL,
                "작품", "설명", ArtworkField.ILLUSTRATION, CreativeType.ORIGINAL,
                List.of(), List.of(), null, List.of(),
                AgeRating.ALL, List.of(Language.KO), true, List.of(), List.of(), null, null, List.of(), List.of()));

        // 처리 중(PROCESSING) 작품은 본인만 열람할 수 있어 작성자 토큰으로 조회한다.
        mockMvc.perform(get("/api/members/{handle}/artworks/{code}", author.handle().toUpperCase(), uploaded.publicCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(uploaded.id()))
                .andExpect(jsonPath("$.data.authorHandle").value(author.handle()))
                .andExpect(jsonPath("$.data.publicCode").value(uploaded.publicCode()));
        mockMvc.perform(get("/api/artworks/{artworkId}", uploaded.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorHandle").value(author.handle()))
                .andExpect(jsonPath("$.data.publicCode").value(uploaded.publicCode()));
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return "sec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@test.com";
    }

    private String uniqueHandle() {
        return "sec" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
