package com.atcrew.auth.docs;

import com.atcrew.member.AuthProvider;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.persistence.MemberRepository;
import com.atcrew.support.RestDocsIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 에러 응답 restdocs-api-spec 문서화 테스트.
 *
 * <p>{@code AuthErrorCode}의 고유 코드마다 대표 API 호출 1개씩만 실제로 발생시켜
 * REST Docs 스니펫과 openapi3 예시를 생성한다.
 *
 * <p>MEMBER_NOT_REGISTERED · INVALID_FIREBASE_TOKEN · UNSUPPORTED_AUTH_PROVIDER는 이 클래스에서
 * 다루지 않는다 — 테스트 환경은 {@code firebase.credentials-path}가 비어 있어 실제 FirebaseVerifier
 * 빈 대신 {@code FirebaseFallbackConfig}의 NoOp 빈이 활성화되고, Google 로그인/가입 호출은 토큰 내용과
 * 무관하게 항상 FIREBASE_NOT_CONFIGURED로 먼저 막힌다. 세 코드는 실제 Firebase 토큰 검증이 성공/실패
 * 분기에 도달해야 발생하므로 공개 API로는 트리거할 수 없다.
 */
class AuthControllerErrorApiSpecTest extends RestDocsIntegrationSupport {

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 비밀번호_불일치로_로그인시_401_AUTHENTICATION_FAILED() throws Exception {
        RegisteredMember member = registerMember("인증실패유저");

        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(member.email(), "WrongPass1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andDo(document("auth/email-login-authentication-failed",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("이메일 또는 비밀번호 불일치로 로그인 시도 — 401 AUTHENTICATION_FAILED")));
    }

    @Test
    void 마이그레이션_회원_로그인시_428_PASSWORD_RESET_REQUIRED() throws Exception {
        RegisteredMember member = registerMember("마이그레이션유저");

        // 라이트에서 이관된 회원(passwordHash == null) 상태를 직접 재현한다.
        Member entity = memberRepository.findByLoginEmailAndAuthProvider(member.email(), AuthProvider.EMAIL)
                .orElseThrow();
        entity.changePassword(null);
        memberRepository.saveAndFlush(entity);

        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(member.email(), "Secure1!"))))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"))
                .andDo(document("auth/email-login-password-reset-required",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("라이트에서 이관되어 비밀번호가 설정되지 않은 회원의 로그인 시도 — 428 PASSWORD_RESET_REQUIRED")));
    }

    @Test
    void 연속_로그인_실패시_429_TOO_MANY_ATTEMPTS() throws Exception {
        RegisteredMember member = registerMember("차단유저");

        // 이메일당 5회 실패로 차단 — 5회 채운 뒤 6번째 시도가 429로 막힌다.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/email/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest(member.email(), "WrongPass1!"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(member.email(), "Secure1!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andDo(document("auth/email-login-too-many-attempts",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("같은 이메일로 10분 내 5회 연속 로그인 실패 후 재시도 — 429 TOO_MANY_ATTEMPTS")));
    }

    @Test
    void Firebase_미설정_환경에서_Google_로그인시_503_FIREBASE_NOT_CONFIGURED() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firebaseIdToken", "any-token-value");

        mockMvc.perform(post("/api/auth/google/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FIREBASE_NOT_CONFIGURED"))
                .andDo(document("auth/google-login-firebase-not-configured",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("Firebase 자격증명이 설정되지 않은 서버에서 Google 로그인 시도 — 503 FIREBASE_NOT_CONFIGURED")));
    }

    @Test
    void 유효하지_않은_토큰으로_갱신시_401_INVALID_REFRESH_TOKEN() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refreshToken", "invalid-refresh-token-" + UUID.randomUUID());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andDo(document("auth/refresh-invalid-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        resource("존재하지 않거나 형식이 잘못된 Refresh Token으로 갱신 시도 — 401 INVALID_REFRESH_TOKEN")));
    }

    /** 이메일 회원가입으로 계정을 생성한다. */
    private RegisteredMember registerMember(String name) throws Exception {
        String uniqueEmail = "auth-errspec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                uniqueEmail, "Secure1!", "Secure1!", name,
                                true, true, true, false, "Asia/Seoul", "KR"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new RegisteredMember(uniqueEmail);
    }

    private record RegisteredMember(String email) {
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

    record LoginRequest(String email, String password) {}
}
