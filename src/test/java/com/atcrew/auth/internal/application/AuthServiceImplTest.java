package com.atcrew.auth.internal.application;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.RegisterCommand;
import com.atcrew.auth.internal.domain.RefreshToken;
import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.infra.firebase.FirebaseUser;
import com.atcrew.auth.internal.infra.firebase.FirebaseVerifier;
import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.RegisterMemberCommand;
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
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    FirebaseVerifier firebaseVerifier;
    MemberService memberService;
    JwtProvider jwtProvider;
    RefreshTokenRepository refreshTokenRepository;
    AuthServiceImpl authService;

    static final String TOKEN = "firebase-id-token";
    static final String EMAIL = "user@test.com";
    static final String MEMBER_ID = "member-001";
    static final String ACCESS_TOKEN = "access.jwt";
    static final String REFRESH_TOKEN = "refresh.jwt";

    @BeforeEach
    void setUp() {
        firebaseVerifier = mock(FirebaseVerifier.class);
        memberService = mock(MemberService.class);
        jwtProvider = mock(JwtProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        authService = new AuthServiceImpl(firebaseVerifier, memberService, jwtProvider, refreshTokenRepository);

        when(jwtProvider.generateAccessToken(anyString(), anyString())).thenReturn(ACCESS_TOKEN);
        when(jwtProvider.generateRefreshToken(anyString())).thenReturn(REFRESH_TOKEN);
        when(jwtProvider.getRefreshExpiry()).thenReturn(Instant.now().plusSeconds(3600));
    }

    // ─── 3. 로그인 흐름 ────────────────────────────────────────────────

    @Test
    void 이메일_로그인_성공() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.login(TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.isNewUser()).isFalse();
        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    @Test
    void Google_로그인_성공() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.GOOGLE));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.GOOGLE));

        AuthInfo result = authService.login(TOKEN);

        assertThat(result.isNewUser()).isFalse();
    }

    @Test
    void 미가입_또는_탈퇴_이메일_로그인_시_단일_에러_반환() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name()));
    }

    @Test
    void Google_가입_계정_이메일_로그인_시도_단일_에러_반환() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.GOOGLE));

        assertThatThrownBy(() -> authService.login(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name()));
    }

    @Test
    void 이메일_가입_계정_Google_로그인_시도_단일_에러_반환() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.EMAIL));

        assertThatThrownBy(() -> authService.login(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name()));
    }

    @Test
    void 로그인_실패_에러_코드가_동일해_enumeration_불가() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));

        // 미가입(또는 탈퇴) 경로
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(false);
        AuthException notFound = (AuthException) catchAuthException(() -> authService.login(TOKEN));

        // provider 불일치 경로
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.GOOGLE));
        AuthException providerMismatch = (AuthException) catchAuthException(() -> authService.login(TOKEN));

        assertThat(notFound.getCode()).isEqualTo(providerMismatch.getCode());
        assertThat(notFound.getCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_FAILED.name());
    }

    @Test
    void 잘못된_Firebase_토큰_로그인() {
        when(firebaseVerifier.verify(TOKEN)).thenThrow(new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN));

        assertThatThrownBy(() -> authService.login(TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_FIREBASE_TOKEN.name()));
    }

    // ─── 2. 가입 흐름 ────────────────────────────────────────────────

    @Test
    void 이메일_창작자_가입_성공() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.register(any(RegisterMemberCommand.class))).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.register(new RegisterCommand(TOKEN, "홍길동", true, true, false));

        assertThat(result.isNewUser()).isTrue();
        verify(memberService).register(argThat(cmd ->
                cmd.authProvider() == AuthProvider.EMAIL &&
                "홍길동".equals(cmd.name()) &&
                cmd.agreePrivacy() && cmd.agreeService()
        ));
    }

    @Test
    void 이메일_미인증_가입_시_422() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, false));

        assertThatThrownBy(() ->
                authService.register(new RegisterCommand(TOKEN, "홍길동", true, true, false))
        ).isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED.name()));
    }

    @Test
    void Google_가입은_emailVerified_무관하게_성공() {
        // Google 토큰은 항상 verified=true이지만, 이 검증이 provider 무관하게 동작함을 확인
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.GOOGLE, true));
        when(memberService.register(any(RegisterMemberCommand.class))).thenReturn(memberInfo(AuthProvider.GOOGLE));

        AuthInfo result = authService.register(new RegisterCommand(TOKEN, "작가이름", true, true, false));

        assertThat(result.isNewUser()).isTrue();
    }

    @Test
    void 중복_이메일_가입_시_도메인_예외_전파() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.register(any(RegisterMemberCommand.class)))
                .thenThrow(new MemberException(MemberErrorCode.DUPLICATE_EMAIL, EMAIL));

        assertThatThrownBy(() ->
                authService.register(new RegisterCommand(TOKEN, "홍길동", true, true, false))
        ).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ─── 4. Refresh Token 흐름 ────────────────────────────────────────

    @Test
    void 정상_토큰_갱신() {
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findAndDeleteByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.of(stored));
        when(memberService.findById(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        AuthInfo result = authService.refresh(REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.isNewUser()).isFalse();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void 만료된_refresh_토큰_갱신_시_401() {
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.name()));
    }

    @Test
    void refresh_토큰_재사용_시_401() {
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findAndDeleteByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.name()));
    }

    @Test
    void 탈퇴_회원_refresh_토큰_갱신_시_403_전파() {
        RefreshToken stored = RefreshToken.of(MEMBER_ID, REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(jwtProvider.validateRefreshToken(REFRESH_TOKEN)).thenReturn(true);
        when(refreshTokenRepository.findAndDeleteByTokenValue(REFRESH_TOKEN)).thenReturn(Optional.of(stored));
        when(memberService.findById(MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, MEMBER_ID));

        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 로그인_시_기존_refresh_토큰_삭제() {
        when(firebaseVerifier.verify(TOKEN)).thenReturn(new FirebaseUser(EMAIL, AuthProvider.EMAIL, true));
        when(memberService.existsByLoginEmail(EMAIL)).thenReturn(true);
        when(memberService.findByLoginEmail(EMAIL)).thenReturn(memberInfo(AuthProvider.EMAIL));
        when(memberService.recordLogin(MEMBER_ID)).thenReturn(memberInfo(AuthProvider.EMAIL));

        authService.login(TOKEN);

        verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────

    private MemberInfo memberInfo(AuthProvider provider) {
        return new MemberInfo(MEMBER_ID, "handle", EMAIL,
                provider,
                "테스트", null, null, List.of(), null, List.of(), List.of(),
                5, 5, null, null, null, List.of(),
                true, null, null, Instant.now(), Instant.now());
    }

    private Exception catchAuthException(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
