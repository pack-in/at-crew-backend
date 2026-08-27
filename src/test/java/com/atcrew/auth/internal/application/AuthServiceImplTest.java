package com.atcrew.auth.internal.application;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.EmailLoginCommand;
import com.atcrew.auth.EmailRegisterCommand;
import com.atcrew.auth.GoogleRegisterCommand;
import com.atcrew.auth.internal.domain.PasswordResetToken;
import com.atcrew.auth.internal.domain.RefreshToken;
import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.infra.firebase.FirebaseUser;
import com.atcrew.auth.internal.infra.firebase.FirebaseVerifier;
import com.atcrew.auth.internal.persistence.PasswordResetTokenRepository;
import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.mail.MailSender;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.Language;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.PasswordVerification;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    FirebaseVerifier firebaseVerifier;
    MemberService memberService;
    JwtProvider jwtProvider;
    RefreshTokenRepository refreshTokenRepository;
    LoginAttemptLimiter loginAttemptLimiter;
    PasswordResetTokenRepository passwordResetTokenRepository;
    PasswordResetAttemptLimiter passwordResetAttemptLimiter;
    MailSender mailSender;
    AuthServiceImpl authService;

    static final String TOKEN = "firebase-id-token";
    static final String EMAIL = "user@test.com";
    static final String PASSWORD = "Pass1234!";
    static final String MEMBER_ID = "member-001";
    static final String ACCESS_TOKEN = "access.jwt";
    static final String REFRESH_TOKEN = "refresh.jwt";
    static final String FRONTEND_BASE_URL = "https://at-crew.com";

    @BeforeEach
    void setUp() {
        firebaseVerifier = mock(FirebaseVerifier.class);
        memberService = mock(MemberService.class);
        jwtProvider = mock(JwtProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        loginAttemptLimiter = mock(LoginAttemptLimiter.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        passwordResetAttemptLimiter = mock(PasswordResetAttemptLimiter.class);
        mailSender = mock(MailSender.class);
        authService = new AuthServiceImpl(firebaseVerifier, memberService, jwtProvider,
                refreshTokenRepository, loginAttemptLimiter, passwordResetTokenRepository,
                passwordResetAttemptLimiter, mailSender, FRONTEND_BASE_URL);

        when(jwtProvider.generateAccessToken(anyString(), anyString())).thenReturn(ACCESS_TOKEN);
        when(jwtProvider.generateRefreshToken(anyString())).thenReturn(REFRESH_TOKEN);
        when(jwtProvider.getRefreshExpiry()).thenReturn(Instant.now().plusSeconds(3600));
    }

    // ─── 이메일 로그인 ────────────────────────────────────────────────

    @Test
    void 이메일_로그인_성공() {
        // N1: verifyPassword가 memberId를 반환해 findByLoginEmailAndProvider 조회 없이 recordLogin 바로 호출
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.matched(MEMBER_ID));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD));

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.isNewUser()).isFalse();
        verify(loginAttemptLimiter).reset(EMAIL);
        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
        verify(memberService, never()).findByLoginEmailAndProvider(anyString(), any());
    }

    @Test
    void 이메일_로그인_실패_단일_401() {
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.mismatched());

        assertThatThrownBy(() -> authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name()));

        verify(loginAttemptLimiter).recordFailure(EMAIL);
    }

    @Test
    void 미가입_탈퇴_비밀번호오답_모두_동일_에러코드로_enumeration_불가() {
        // MISMATCHED는 미가입/탈퇴/비밀번호 오답을 모두 같은 값으로 반환 — auth는 구분 불가
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.mismatched());

        AuthException result = catchAuthException(
                () -> authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD)));

        assertThat(result.getCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name());
    }

    @Test
    void 마이그레이션_회원_로그인_428() {
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.notSet());

        assertThatThrownBy(() -> authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.PASSWORD_RESET_REQUIRED.name()));

        // N3: 마이그레이션 회원도 실패 횟수 누적 (레이트리밋 우회 방지)
        verify(loginAttemptLimiter).recordFailure(EMAIL);
    }

    @Test
    void Rate_limit_초과_시_BCrypt_전에_429() {
        doThrow(new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS))
                .when(loginAttemptLimiter).checkBlocked(EMAIL);

        assertThatThrownBy(() -> authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS.name()));

        verify(memberService, never()).verifyPassword(anyString(), anyString());
    }

    // ─── Google 로그인 ────────────────────────────────────────────────

    @Test
    void Google_로그인_성공() {
        // F3: existsByLoginEmailAndProvider 제거 — findByLoginEmailAndProvider 단일 조회
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.findByLoginEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(memberInfo(AuthProvider.GOOGLE));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.GOOGLE));

        AuthInfo result = authService.loginWithGoogle(TOKEN);

        assertThat(result.isNewUser()).isFalse();
        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    @Test
    void Google_미가입_404() {
        // F3: not-found는 findByLoginEmailAndProvider 예외로 처리
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.findByLoginEmailAndProvider(EMAIL, AuthProvider.GOOGLE))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, EMAIL));

        assertThatThrownBy(() -> authService.loginWithGoogle(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.MEMBER_NOT_REGISTERED.name()));
    }

    @Test
    void Google_Firebase_토큰_오류_401() {
        when(firebaseVerifier.verify(TOKEN)).thenThrow(new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN));

        assertThatThrownBy(() -> authService.loginWithGoogle(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_FIREBASE_TOKEN.name()));
    }

    // ─── 이메일 회원가입 ──────────────────────────────────────────────

    @Test
    void 이메일_회원가입_성공() {
        when(memberService.register(any())).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.registerWithEmail(
                new EmailRegisterCommand(EMAIL, "Pass1234!", "홍길동", true, true, true, false, "Asia/Seoul", "KR", Language.KO));

        assertThat(result.isNewUser()).isTrue();
        verify(memberService).register(argThat(cmd ->
                cmd.authProvider() == AuthProvider.EMAIL &&
                "홍길동".equals(cmd.name()) &&
                cmd.agreePrivacy() && cmd.agreeService() && cmd.agreeThirdParty()
        ));
    }

    @Test
    void 이메일_중복_가입_409_전파() {
        when(memberService.register(any()))
                .thenThrow(new MemberException(MemberErrorCode.DUPLICATE_EMAIL, EMAIL));

        assertThatThrownBy(() -> authService.registerWithEmail(
                new EmailRegisterCommand(EMAIL, "Pass1234!", "홍길동", true, true, true, false, "Asia/Seoul", "KR", Language.KO)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ─── Google 회원가입 ──────────────────────────────────────────────

    @Test
    void Google_회원가입_성공() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.register(any())).thenReturn(memberInfo(AuthProvider.GOOGLE));

        AuthInfo result = authService.registerWithGoogle(
                new GoogleRegisterCommand(TOKEN, "홍길동", true, true, true, false, "Asia/Seoul", "KR", Language.KO));

        assertThat(result.isNewUser()).isTrue();
        verify(memberService).register(argThat(cmd ->
                cmd.authProvider() == AuthProvider.GOOGLE && cmd.rawPassword() == null));
    }

    // ─── Refresh Token 흐름 ───────────────────────────────────────────

    @Test
    void 정상_토큰_갱신() {
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        givenStoredToken(stored);
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.refresh(REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.isNewUser()).isFalse();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void 만료된_refresh_토큰_401() {
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.name()));
    }

    @Test
    void refresh_토큰_재사용_401() {
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenValueAndExpiresAtAfter(eq(REFRESH_TOKEN), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.name()));
    }

    @Test
    void 동시_요청에_토큰을_먼저_소비당하면_401() {
        // MariaDB 전환(§3.3.2) — 조회는 성공했으나 DELETE 영향 행 수가 0이면 이미 소비된 토큰으로 판별
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findByTokenValueAndExpiresAtAfter(eq(REFRESH_TOKEN), any()))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.deleteByIdReturningCount(stored.getId())).thenReturn(0);

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.name()));

        verify(memberService, never()).findById(anyString());
    }

    @Test
    void 탈퇴_회원_refresh_토큰_갱신_시_403_전파() {
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        givenStoredToken(stored);
        when(memberService.findById(MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, MEMBER_ID));

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 로그인_시_기존_refresh_토큰_삭제() {
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.matched(MEMBER_ID));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        authService.loginWithEmail(new EmailLoginCommand(EMAIL, PASSWORD));

        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    // ─── 로그아웃 ─────────────────────────────────────────────────────

    @Test
    void 로그아웃_시_refresh_토큰_삭제() {
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        givenStoredToken(stored);

        authService.logout(MEMBER_ID, REFRESH_TOKEN);

        verify(refreshTokenRepository).deleteByIdReturningCount(stored.getId());
    }

    @Test
    void 이미_로그아웃된_토큰이면_조용히_성공() {
        when(refreshTokenRepository.findByTokenValueAndExpiresAtAfter(eq(REFRESH_TOKEN), any()))
                .thenReturn(Optional.empty());

        authService.logout(MEMBER_ID, REFRESH_TOKEN);

        verify(refreshTokenRepository, never()).deleteByIdReturningCount(anyString());
    }

    @Test
    void 타인_소유_refresh_토큰은_로그아웃되지_않음() {
        RefreshToken stored = RefreshToken.of("other-member", REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        givenStoredToken(stored);

        authService.logout(MEMBER_ID, REFRESH_TOKEN);

        verify(refreshTokenRepository, never()).deleteByIdReturningCount(anyString());
    }

    // ─── 비밀번호 변경 ────────────────────────────────────────────────

    @Test
    void 비밀번호_변경_성공_시_refresh_토큰_전체_폐기() {
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.matched(MEMBER_ID));

        authService.changePassword(MEMBER_ID, PASSWORD, "NewPass1!");

        verify(memberService).changePassword(MEMBER_ID, "NewPass1!");
        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    @Test
    void 현재_비밀번호_불일치_시_변경_거부() {
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.mismatched());

        assertThatThrownBy(() -> authService.changePassword(MEMBER_ID, PASSWORD, "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.CURRENT_PASSWORD_MISMATCH.name()));

        verify(memberService, never()).changePassword(anyString(), anyString());
    }

    @Test
    void GOOGLE_계정은_비밀번호_변경_불가() {
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.GOOGLE));

        assertThatThrownBy(() -> authService.changePassword(MEMBER_ID, PASSWORD, "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED.name()));

        verify(memberService, never()).verifyPassword(anyString(), anyString());
        verify(memberService, never()).changePassword(anyString(), anyString());
    }

    @Test
    void 마이그레이션_회원_비밀번호_변경_428() {
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.verifyPassword(EMAIL, PASSWORD)).thenReturn(PasswordVerification.notSet());

        assertThatThrownBy(() -> authService.changePassword(MEMBER_ID, PASSWORD, "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.PASSWORD_RESET_REQUIRED.name()));

        verify(memberService, never()).changePassword(anyString(), anyString());
    }

    // ─── 비밀번호 재설정 ──────────────────────────────────────────────

    @Test
    void 재설정_요청_EMAIL_회원_존재시_메일_발송() {
        when(memberService.findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.EMAIL))
                .thenReturn(Optional.of(memberInfo(AuthProvider.EMAIL)));

        authService.requestPasswordReset(EMAIL);

        verify(passwordResetAttemptLimiter).checkBlocked(EMAIL);
        verify(passwordResetAttemptLimiter).recordAttempt(EMAIL);
        verify(passwordResetTokenRepository).deleteAllByMemberId(MEMBER_ID);
        verify(passwordResetTokenRepository).save(any());
        verify(mailSender).send(eq(EMAIL), anyString(), contains(FRONTEND_BASE_URL + "/reset-password?token="));
        // GOOGLE 계정 조회는 EMAIL 계정이 있으면 시도하지 않는다
        verify(memberService, never()).findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.GOOGLE);
    }

    @Test
    void 재설정_요청_GOOGLE_계정만_있으면_안내메일_발송() {
        when(memberService.findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.EMAIL))
                .thenReturn(Optional.empty());
        when(memberService.findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.GOOGLE))
                .thenReturn(Optional.of(memberInfo(AuthProvider.GOOGLE)));

        authService.requestPasswordReset(EMAIL);

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailSender).send(eq(EMAIL), contains("Google"), anyString());
    }

    @Test
    void 재설정_요청_미가입이면_메일_미발송_예외없이_종료() {
        when(memberService.findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.EMAIL))
                .thenReturn(Optional.empty());
        when(memberService.findActiveByLoginEmailAndProviderOrEmpty(EMAIL, AuthProvider.GOOGLE))
                .thenReturn(Optional.empty());

        authService.requestPasswordReset(EMAIL);

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 재설정_요청_rate_limit_초과시_전파() {
        doThrow(new AuthException(AuthErrorCode.TOO_MANY_ATTEMPTS))
                .when(passwordResetAttemptLimiter).checkBlocked(EMAIL);

        assertThatThrownBy(() -> authService.requestPasswordReset(EMAIL))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.TOO_MANY_ATTEMPTS.name()));

        verify(memberService, never()).findActiveByLoginEmailAndProviderOrEmpty(anyString(), any());
    }

    @Test
    void 재설정_확정_성공_시_비밀번호변경_및_refresh토큰_전체폐기() {
        PasswordResetToken stored = PasswordResetToken.of(MEMBER_ID, "hash", Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(passwordResetTokenRepository.deleteByIdReturningCount(stored.getId())).thenReturn(1);

        authService.confirmPasswordReset("raw-token", "NewPass1!");

        verify(memberService).changePassword(MEMBER_ID, "NewPass1!");
        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    @Test
    void 재설정_확정_토큰_없거나_만료시_401() {
        when(passwordResetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmPasswordReset("raw-token", "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN.name()));

        verify(memberService, never()).changePassword(anyString(), anyString());
    }

    @Test
    void 재설정_확정_동시_재사용시_401() {
        // §3.3.2와 동일한 findAndDelete 패턴 — 조회는 성공했으나 DELETE 영향 행 수 0이면 이미 소비된 토큰
        PasswordResetToken stored = PasswordResetToken.of(MEMBER_ID, "hash", Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(stored));
        when(passwordResetTokenRepository.deleteByIdReturningCount(stored.getId())).thenReturn(0);

        assertThatThrownBy(() -> authService.confirmPasswordReset("raw-token", "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN.name()));

        verify(memberService, never()).changePassword(anyString(), anyString());
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────

    // 토큰 소비 성공 스텁 — 만료 조건 포함 조회 + DELETE 영향 행 수 1 (§3.3.2)
    private void givenStoredToken(RefreshToken stored) {
        when(refreshTokenRepository.findByTokenValueAndExpiresAtAfter(eq(stored.getTokenValue()), any()))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.deleteByIdReturningCount(stored.getId())).thenReturn(1);
    }

    private MemberInfo memberInfo(AuthProvider provider) {
        return new MemberInfo(MEMBER_ID, "handle", EMAIL,
                provider,
                "테스트", null, List.of(), null, null, List.of(),
                List.of(), null, null, List.of(), List.of(), List.of(), null, List.of(), null, null, List.of(),
                5, 5, null, null, null, List.of(),
                true, null, null, Instant.now(), Instant.now(), "Asia/Seoul", "KR", Language.KO, List.of(Language.KO), false, false);
    }

    private AuthException catchAuthException(Runnable action) {
        try {
            action.run();
            return null;
        } catch (AuthException e) {
            return e;
        }
    }
}
