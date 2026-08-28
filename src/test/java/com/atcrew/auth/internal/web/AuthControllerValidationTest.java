package com.atcrew.auth.internal.web;

import com.atcrew.auth.AuthService;
import com.atcrew.common.security.SecurityUtils;
import com.atcrew.common.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 입력 검증 단위 테스트.
 *
 * <p>실제 서비스 없이 standaloneSetup으로 컨트롤러만 기동하여 Bean Validation 규칙을 검증한다.
 * 각 케이스에 REST Docs 스니펫을 생성해 잘못된 요청의 응답 형태를 문서화한다.
 */
@ExtendWith(RestDocumentationExtension.class)
class AuthControllerValidationTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    AuthService authService;
    SecurityUtils securityUtils;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDoc) {
        authService = mock(AuthService.class);
        securityUtils = mock(SecurityUtils.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, securityUtils))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDoc)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    // ─── POST /api/auth/email/login ───────────────────────────────────

    @Test
    void 이메일_로그인_email_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "", "password", "Pass1234!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-login-blank-email"));
    }

    @Test
    void 이메일_로그인_email_형식_오류_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-email", "password", "Pass1234!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-login-invalid-email-format"));
    }

    @Test
    void 이메일_로그인_password_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "user@test.com", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-login-blank-password"));
    }

    @Test
    void 이메일_로그인_password_정책_위반_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "user@test.com", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-login-invalid-password-policy"));
    }

    // ─── POST /api/auth/email/register ───────────────────────────────

    @Test
    void 이메일_가입_email_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("", "Pass1234!", "Pass1234!", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-register-blank-email"));
    }

    @Test
    void 이메일_가입_name_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "Pass1234!", "Pass1234!", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-register-blank-name"));
    }

    @Test
    void 이메일_가입_name_16자_초과_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "Pass1234!", "Pass1234!", "가".repeat(17))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-register-name-too-long"));
    }

    @Test
    void 이메일_가입_비밀번호_불일치_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("user@test.com", "Pass1234!", "Different1!", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/email-register-password-mismatch"));
    }

    // ─── POST /api/auth/google/login ─────────────────────────────────

    @Test
    void Google_로그인_firebaseIdToken_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firebaseIdToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/google-login-blank-token"));
    }

    // ─── POST /api/auth/google/register ──────────────────────────────

    @Test
    void Google_가입_firebaseIdToken_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/google/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firebaseIdToken", "",
                                "name", "홍길동",
                                "agreePrivacy", true, "agreeService", true, "agreeThirdParty", true, "agreeMarketing", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/google-register-blank-token"));
    }

    @Test
    void Google_가입_name_16자_초과_400() throws Exception {
        mockMvc.perform(post("/api/auth/google/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firebaseIdToken", "valid-token",
                                "name", "가".repeat(17),
                                "agreePrivacy", true, "agreeService", true, "agreeThirdParty", true, "agreeMarketing", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/google-register-name-too-long"));
    }

    // ─── POST /api/auth/refresh ───────────────────────────────────────

    @Test
    void refresh_토큰_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/refresh-blank-token"));
    }

    // ─── POST /api/auth/logout ────────────────────────────────────────

    @Test
    void 로그아웃_토큰_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/logout-blank-token"));
    }

    // ─── POST /api/auth/email/password-change ─────────────────────────

    @Test
    void 비밀번호_변경_현재_비밀번호_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("", "NewPass1!", "NewPass1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-change-blank-current"));
    }

    @Test
    void 비밀번호_변경_새_비밀번호_정책_위반_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("OldPass1!", "short", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-change-invalid-policy"));
    }

    @Test
    void 비밀번호_변경_확인값_불일치_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody("OldPass1!", "NewPass1!", "Different1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-change-confirm-mismatch"));
    }

    @Test
    void 비밀번호_변경_refreshToken_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "OldPass1!",
                                "newPassword", "NewPass1!",
                                "newPasswordConfirm", "NewPass1!",
                                "refreshToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-change-blank-refresh-token"));
    }

    @Test
    void 비밀번호_재설정_요청_email_형식_오류_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-reset-request-invalid-email"));
    }

    @Test
    void 비밀번호_재설정_확정_token_blank_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmBody("", "NewPass1!", "NewPass1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-reset-confirm-blank-token"));
    }

    @Test
    void 비밀번호_재설정_확정_새_비밀번호_정책_위반_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmBody("some-token", "short", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-reset-confirm-invalid-policy"));
    }

    @Test
    void 비밀번호_재설정_확정_확인값_불일치_400() throws Exception {
        mockMvc.perform(post("/api/auth/email/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordResetConfirmBody("some-token", "NewPass1!", "Different1!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andDo(document("auth/validation/password-reset-confirm-mismatch"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────

    private String passwordChangeBody(String current, String next, String confirm) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "currentPassword", current,
                "newPassword", next,
                "newPasswordConfirm", confirm,
                "refreshToken", "refresh.jwt"
        ));
    }

    private String passwordResetConfirmBody(String token, String next, String confirm) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "token", token,
                "newPassword", next,
                "newPasswordConfirm", confirm
        ));
    }


    private String registerBody(String email, String password, String passwordConfirm, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password,
                "passwordConfirm", passwordConfirm,
                "name", name,
                "agreeService", true,
                "agreePrivacy", true,
                "agreeThirdParty", true,
                "agreeMarketing", false
        ));
    }
}
