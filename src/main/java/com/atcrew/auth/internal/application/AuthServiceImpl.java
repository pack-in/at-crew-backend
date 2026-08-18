package com.atcrew.auth.internal.application;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.EmailLoginCommand;
import com.atcrew.auth.EmailRegisterCommand;
import com.atcrew.auth.GoogleRegisterCommand;
import com.atcrew.auth.internal.domain.RefreshToken;
import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.auth.internal.infra.firebase.FirebaseUser;
import com.atcrew.auth.internal.infra.firebase.FirebaseVerifier;
import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.logging.LogMask;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.PasswordVerification;
import com.atcrew.member.RegisterMemberCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final FirebaseVerifier firebaseVerifier;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;

    AuthServiceImpl(FirebaseVerifier firebaseVerifier, MemberService memberService,
                    JwtProvider jwtProvider, RefreshTokenRepository refreshTokenRepository,
                    LoginAttemptLimiter loginAttemptLimiter) {
        this.firebaseVerifier = firebaseVerifier;
        this.memberService = memberService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Override
    @Transactional
    public AuthInfo loginWithEmail(EmailLoginCommand command) {
        // 1. Rate limit 선검사 — BCrypt 연산 전에 차단 (DoS 증폭 방지)
        loginAttemptLimiter.checkBlocked(command.email());

        // 2. 비밀번호 검증 (timing-safe — 회원 부재 시에도 더미 BCrypt 수행)
        //    N1: MATCHED 시 memberId를 함께 반환 → 중복 findByLoginEmailAndProvider 조회 제거
        PasswordVerification verification = memberService.verifyPassword(command.email(), command.password());

        // 3. 마이그레이션 회원 분기 (존재 노출 불가피 — §1.2 근거)
        //    N3: 마이그레이션 회원도 시도 횟수 누적 (레이트리밋 우회 방지)
        if (verification.isNotSet()) {
            loginAttemptLimiter.recordFailure(command.email());
            throw new AuthException(AuthErrorCode.PASSWORD_RESET_REQUIRED);
        }

        // 4. 실패 통합 처리 (미가입·탈퇴·비밀번호 오답 모두 동일 401)
        if (verification.isMismatched()) {
            loginAttemptLimiter.recordFailure(command.email());
            log.warn("이메일 로그인 실패: email={}", LogMask.email(command.email()));
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }

        // 5. 로그인 성공 — verification.memberId()로 findByLoginEmailAndProvider 조회 생략
        loginAttemptLimiter.reset(command.email());
        MemberInfo member = memberService.recordLogin(verification.memberId());

        String accessToken = jwtProvider.generateAccessToken(member.id(), command.email());
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("이메일 로그인 성공: memberId={} email={}", member.id(), LogMask.email(command.email()));
        return new AuthInfo(accessToken, refreshTokenValue, member, false);
    }

    @Override
    @Transactional
    public AuthInfo loginWithGoogle(String firebaseIdToken) {
        FirebaseUser firebaseUser = firebaseVerifier.verify(firebaseIdToken);
        String email = firebaseUser.email();

        // Firebase 토큰이 이메일 소유를 증명 → 404 노출은 enumeration 아님 (§1.2)
        // F3: exists + find 2회 조회 → find 1회로 최적화 (not-found는 DomainException으로 처리)
        MemberInfo member;
        try {
            member = memberService.findByLoginEmailAndProvider(email, AuthProvider.GOOGLE);
        } catch (DomainException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                log.info("Google 로그인[미가입]: email={}", LogMask.email(email));
                throw new AuthException(AuthErrorCode.MEMBER_NOT_REGISTERED);
            }
            throw e;
        }
        member = memberService.recordLogin(member.id());

        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("Google 로그인 성공: memberId={} email={}", member.id(), LogMask.email(email));
        return new AuthInfo(accessToken, refreshTokenValue, member, false);
    }

    @Override
    @Transactional
    public AuthInfo registerWithEmail(EmailRegisterCommand command) {
        RegisterMemberCommand memberCommand = new RegisterMemberCommand(
                command.email(), command.name(), AuthProvider.EMAIL, command.password(),
                command.agreePrivacy(), command.agreeService(), command.agreeThirdParty(), command.agreeMarketing(),
                command.timezone(), command.countryCode());

        MemberInfo member = memberService.register(memberCommand);

        String accessToken = jwtProvider.generateAccessToken(member.id(), command.email());
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("이메일 회원가입 성공: memberId={} email={}", member.id(), LogMask.email(command.email()));
        return new AuthInfo(accessToken, refreshTokenValue, member, true);
    }

    @Override
    @Transactional
    public AuthInfo registerWithGoogle(GoogleRegisterCommand command) {
        FirebaseUser firebaseUser = firebaseVerifier.verify(command.firebaseIdToken());
        String email = firebaseUser.email();

        RegisterMemberCommand memberCommand = new RegisterMemberCommand(
                email, command.name(), AuthProvider.GOOGLE, null,
                command.agreePrivacy(), command.agreeService(), command.agreeThirdParty(), command.agreeMarketing(),
                command.timezone(), command.countryCode());

        MemberInfo member = memberService.register(memberCommand);

        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("Google 회원가입 성공: memberId={} email={}", member.id(), LogMask.email(email));
        return new AuthInfo(accessToken, refreshTokenValue, member, true);
    }

    @Override
    @Transactional
    public AuthInfo refresh(String refreshToken) {
        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 단일 소비 보장 — 동시 요청 TOCTOU 방지
        RefreshToken stored = consumeRefreshToken(refreshToken);

        MemberInfo member;
        try {
            member = memberService.findById(stored.getMemberId());
        } catch (DomainException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                throw e;
            }
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtProvider.generateAccessToken(member.id(), member.loginEmail());
        String newRefreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), newRefreshTokenValue, jwtProvider.getRefreshExpiry()));

        member = memberService.recordLogin(member.id());

        log.info("토큰 갱신: memberId={}", member.id());
        return new AuthInfo(newAccessToken, newRefreshTokenValue, member, false);
    }

    @Override
    @Transactional
    public void logout(String memberId, String refreshToken) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenValueAndExpiresAtAfter(refreshToken, Instant.now())
                .orElse(null);

        // 이미 로그아웃됐거나 만료된 토큰 — 재요청·중복 클릭에서 흔하므로 에러로 취급하지 않는다.
        if (stored == null) {
            return;
        }
        // 남의 refresh token을 넘겨 강제 로그아웃시키는 것을 차단한다.
        // 토큰의 존재 여부를 알려주지 않도록 응답은 성공 경로와 동일하게 둔다.
        if (!stored.getMemberId().equals(memberId)) {
            log.warn("타 회원 소유 refresh token 로그아웃 시도: memberId={}", memberId);
            return;
        }

        refreshTokenRepository.deleteByIdReturningCount(stored.getId());
        log.info("로그아웃: memberId={}", memberId);
    }

    @Override
    @Transactional
    public void changePassword(String memberId, String currentPassword, String newPassword) {
        MemberInfo member = memberService.findById(memberId);

        // GOOGLE 계정은 비밀번호 자체가 없어 변경 대상이 아니다.
        if (member.authProvider() != AuthProvider.EMAIL) {
            throw new AuthException(AuthErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED);
        }

        PasswordVerification verification = memberService.verifyPassword(member.loginEmail(), currentPassword);
        if (verification.isNotSet()) {
            // 마이그레이션 회원 — 확인할 현재 비밀번호가 없으므로 재설정 경로로 유도한다.
            throw new AuthException(AuthErrorCode.PASSWORD_RESET_REQUIRED);
        }
        if (!verification.isMatched()) {
            log.warn("비밀번호 변경 실패(현재 비밀번호 불일치): memberId={}", memberId);
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        memberService.changePassword(memberId, newPassword);
        // 비밀번호가 바뀌면 기존에 유출됐을 수 있는 refresh token도 함께 끊는다 — 재로그인이 필요하다.
        refreshTokenRepository.deleteAllByMemberId(memberId);

        log.info("비밀번호 변경: memberId={}", memberId);
    }

    // Mongo findAndRemove 대체 (docs/design/mariadb-migration-design.md §3.3.2) —
    // 조회 후 조건부 DELETE의 영향 행 수로 승자를 결정한다. 동시 요청 두 개가 같은 토큰을 들고 와도
    // 1을 가져가는 쪽은 하나뿐이므로 "하나만 토큰을 소비한다"는 보장이 유지된다.
    private RefreshToken consumeRefreshToken(String tokenValue) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenValueAndExpiresAtAfter(tokenValue, Instant.now())
                .orElse(null);

        if (stored == null || refreshTokenRepository.deleteByIdReturningCount(stored.getId()) == 0) {
            log.warn("refresh token 미존재 또는 재사용 시도 — 탈취 가능성");
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return stored;
    }

    private String issueRefreshToken(String memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
        String refreshTokenValue = jwtProvider.generateRefreshToken(memberId);
        refreshTokenRepository.save(RefreshToken.of(memberId, refreshTokenValue, jwtProvider.getRefreshExpiry()));
        return refreshTokenValue;
    }
}
