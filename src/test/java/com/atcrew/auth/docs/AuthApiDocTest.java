package com.atcrew.auth.docs;

import com.atcrew.auth.internal.domain.PasswordResetToken;
import com.atcrew.auth.internal.persistence.PasswordResetTokenRepository;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 API 문서화 통합 테스트.
 *
 * <p>실제 MariaDB Testcontainer와 전체 Spring 컨텍스트를 기동해
 * 이메일 회원가입·로그인·토큰 갱신 API의 요청/응답 구조를 REST Docs 스니펫으로 생성한다.
 */
class AuthApiDocTest extends RestDocsIntegrationSupport {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * 이메일 회원가입 성공 시나리오 문서화.
     * 요청 필드와 응답 필드를 모두 스니펫으로 생성한다.
     */
    @Test
    void 이메일_회원가입_성공_문서화() throws Exception {
        String uniqueEmail = "doc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail,
                                "Secure1!",
                                "Secure1!",
                                "문서화유저",
                                true, true, true, false,
                                "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andDo(document("auth/email-register",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호 (영문·숫자·특수문자 포함 8자 이상)"),
                                fieldWithPath("passwordConfirm").description("비밀번호 확인"),
                                fieldWithPath("name").description("이름·작가명 (최대 16자)"),
                                fieldWithPath("agreeService").description("서비스 이용약관 동의 (필수)"),
                                fieldWithPath("agreePrivacy").description("개인정보처리방침 동의 (필수)"),
                                fieldWithPath("agreeThirdParty").description("제3자 정보제공 동의 (선택)"),
                                fieldWithPath("agreeMarketing").description("마케팅 정보 수신 동의 (선택)"),
                                fieldWithPath("timezone").description("IANA 시간대 ID, 클라이언트 자동감지값 (예: Asia/Seoul)"),
                                fieldWithPath("countryCode").description("거주 국가 (ISO 3166-1 alpha-2, 예: KR)"),
                                fieldWithPath("primaryLanguage").description(
                                        "주 사용 언어 (KO·JA·ZH·EN) — 가입 후 변경할 수 없습니다")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("리프레시 토큰 (JWT)"),
                                fieldWithPath("data.isNewUser").description("신규 가입 여부"),
                                fieldWithPath("data.member.id").description("회원 고유 식별자"),
                                fieldWithPath("data.member.handle").description("회원 핸들 (@아이디)"),
                                fieldWithPath("data.member.authProvider").description("인증 제공자 (EMAIL)"),
                                fieldWithPath("data.member.name").description("이름·작가명"),
                                fieldWithPath("data.member.employmentStatus").description("구직 상태"),
                                fieldWithPath("data.member.totalSlotCount").description("총 슬롯 수"),
                                fieldWithPath("data.member.availableSlotCount").description("가용 슬롯 수"),
                                fieldWithPath("data.member.active").description("계정 활성화 여부"),
                                fieldWithPath("data.member.createdAt").description("가입 일시 (ISO 8601)"),
                                fieldWithPath("data.member.updatedAt").description("최종 수정 일시 (ISO 8601)"),
                                fieldWithPath("data.member.timezone").description("회원 시간대 (IANA tz ID)"),
                                fieldWithPath("data.member.countryCode").description("회원 거주 국가 (ISO 3166-1 alpha-2)")
                        )
                ));
    }

    /**
     * 이메일 로그인 성공 시나리오 문서화.
     * 먼저 회원가입 API로 계정을 생성한 후 로그인 API를 호출해 스니펫을 생성한다.
     */
    @Test
    void 이메일_로그인_성공_문서화() throws Exception {
        // 테스트용 계정 먼저 생성
        String uniqueEmail = "doc-login-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "로그인문서유저",
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated());

        // 로그인 API 호출 및 문서화
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "Secure1!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("auth/email-login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("리프레시 토큰 (JWT)"),
                                fieldWithPath("data.isNewUser").description("신규 가입 여부 (로그인 시 false)"),
                                fieldWithPath("data.member.id").description("회원 고유 식별자"),
                                fieldWithPath("data.member.handle").description("회원 핸들 (@아이디)")
                        )
                ));
    }

    /**
     * 토큰 갱신 성공 시나리오 문서화.
     * 회원가입 API 응답에서 리프레시 토큰을 추출해 갱신 API를 호출한다.
     */
    @Test
    void 토큰_갱신_성공_문서화() throws Exception {
        // 회원가입으로 refreshToken 획득
        String uniqueEmail = "doc-refresh-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "갱신문서유저",
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();

        // 응답에서 refreshToken 추출
        String refreshToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .at("/data/refreshToken").asText();

        // 토큰 갱신 API 호출 및 문서화
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andDo(document("auth/token-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("refreshToken").description("리프레시 토큰 (JWT)")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드 (SUCCESS)"),
                                fieldWithPath("data.accessToken").description("새로 발급된 액세스 토큰 (JWT)"),
                                fieldWithPath("data.refreshToken").description("새로 발급된 리프레시 토큰 (JWT)")
                        )
                ));
    }

    /**
     * 비밀번호 변경 성공 시나리오 문서화.
     * 회원가입으로 토큰을 얻은 뒤 현재 비밀번호를 확인받고 새 비밀번호로 교체한다.
     */
    @Test
    void 비밀번호_변경_성공_문서화() throws Exception {
        String uniqueEmail = "doc-pwchange-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String accessToken = registerAndGetAccessToken(uniqueEmail, "Secure1!", "비번변경유저");

        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("Secure1!", "Changed1!", "Changed1!"))))
                .andExpect(status().isNoContent())
                .andDo(document("auth/password-change",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("currentPassword").description("현재 비밀번호"),
                                fieldWithPath("newPassword").description("새 비밀번호 (영문·숫자·특수문자 포함 8자 이상)"),
                                fieldWithPath("newPasswordConfirm").description("새 비밀번호 확인")
                        )
                ));

        // 새 비밀번호로 로그인되는지 확인 (기존 비밀번호는 더 이상 통하지 않음)
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "Secure1!"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "Changed1!"))))
                .andExpect(status().isOk());
    }

    /**
     * 현재 비밀번호가 틀리면 400으로 거부되는지 확인한다.
     */
    @Test
    void 비밀번호_변경_현재_비밀번호_불일치_400() throws Exception {
        String uniqueEmail = "doc-pwwrong-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String accessToken = registerAndGetAccessToken(uniqueEmail, "Secure1!", "비번오답유저");

        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("WrongPass1!", "Changed1!", "Changed1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
    }

    /**
     * 로그아웃 성공 시나리오 문서화.
     * 폐기된 refresh token으로는 더 이상 토큰을 갱신할 수 없음을 함께 검증한다.
     */
    @Test
    void 로그아웃_성공_문서화() throws Exception {
        String uniqueEmail = "doc-logout-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "로그아웃문서유저",
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = registerResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();
        String refreshToken = objectMapper.readTree(body).at("/data/refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent())
                .andDo(document("auth/logout",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("refreshToken").description("폐기할 리프레시 토큰 (JWT)")
                        )
                ));

        // 폐기된 토큰으로는 갱신 불가
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        // 이미 로그아웃된 상태로 재요청해도 204 (멱등)
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());
    }

    /**
     * 비밀번호 재설정 요청 성공 시나리오 문서화. 가입 여부와 무관하게 항상 200을 반환한다
     * (docs/design/auth-email-custom-redesign.md §7.2 — enumeration 방지).
     */
    @Test
    void 비밀번호_재설정_요청_성공_문서화() throws Exception {
        String uniqueEmail = "doc-pwreset-req-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "재설정요청유저",
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/email/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(uniqueEmail))))
                .andExpect(status().isOk())
                .andDo(document("auth/password-reset-request",
                        preprocessRequest(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("가입 시 사용한 이메일 주소")
                        )
                ));
    }

    /**
     * 가입되지 않은 이메일이어도 동일하게 200을 반환해 계정 존재 여부를 노출하지 않는지 확인한다.
     */
    @Test
    void 비밀번호_재설정_요청_미가입_이메일도_200() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetRequestRequest("no-such-account@example.com"))))
                .andExpect(status().isOk());
    }

    /**
     * 비밀번호 재설정 확정 성공 시나리오 문서화. 실제 이메일 발송 없이(메일 어댑터는 best-effort라
     * 테스트 환경에서도 예외 없이 통과한다), 회원가입 후 토큰을 직접 발급해(§7.3 SHA-256 해시 저장과
     * 동일한 방식) 확정 API를 검증한다 — 원문 토큰은 저장하지 않는 설계라 API 응답으로는 얻을 수 없다.
     */
    @Test
    void 비밀번호_재설정_확정_성공_문서화() throws Exception {
        String uniqueEmail = "doc-pwreset-confirm-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", "재설정확정유저",
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();
        String memberId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .at("/data/member/id").asText();

        String rawToken = "doc-test-raw-token-" + UUID.randomUUID();
        passwordResetTokenRepository.save(PasswordResetToken.of(
                memberId, sha256Hex(rawToken), Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest(rawToken, "Reset12!", "Reset12!"))))
                .andExpect(status().isNoContent())
                .andDo(document("auth/password-reset-confirm",
                        preprocessRequest(prettyPrint()),
                        requestFields(
                                fieldWithPath("token").description("이메일로 받은 재설정 토큰"),
                                fieldWithPath("newPassword").description("새 비밀번호 (영문·숫자·특수문자 포함 8자 이상)"),
                                fieldWithPath("newPasswordConfirm").description("새 비밀번호 확인")
                        )
                ));

        // 토큰은 1회용 — 같은 토큰으로 재시도하면 401
        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest(rawToken, "Reset23!", "Reset23!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));

        // 새 비밀번호로 로그인되는지 확인
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(uniqueEmail, "Reset12!"))))
                .andExpect(status().isOk());
    }

    /**
     * 존재하지 않는 토큰으로 확정을 시도하면 401을 반환하는지 확인한다.
     */
    @Test
    void 비밀번호_재설정_확정_유효하지않은_토큰_401() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetConfirmRequest("no-such-token", "Reset12!", "Reset12!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    // AuthServiceImpl.sha256Hex와 동일한 방식(§7.3) — 원문 토큰은 저장하지 않으므로 테스트에서
    // API 응답으로 얻을 수 없어, 서비스가 저장할 값을 직접 계산해 리포지토리에 심어둔다.
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String registerAndGetAccessToken(String email, String password, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email, password, password, name,
                                true, true, true, false, "Asia/Seoul", "KR", "KO"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }

    // ─── 요청 바디 내부 레코드 ───────────────────────────────────────────

    /** 이메일 회원가입 요청 바디 */
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
            String countryCode,
            String primaryLanguage
    ) {}

    /** 이메일 로그인 요청 바디 */
    record LoginRequest(String email, String password) {}

    /** 토큰 갱신 요청 바디 */
    record RefreshRequest(String refreshToken) {}

    /** 로그아웃 요청 바디 */
    record LogoutRequest(String refreshToken) {}

    /** 비밀번호 변경 요청 바디 */
    record ChangePasswordRequest(String currentPassword, String newPassword, String newPasswordConfirm) {}

    /** 비밀번호 재설정 요청 바디 */
    record PasswordResetRequestRequest(String email) {}

    /** 비밀번호 재설정 확정 요청 바디 */
    record PasswordResetConfirmRequest(String token, String newPassword, String newPasswordConfirm) {}
}
