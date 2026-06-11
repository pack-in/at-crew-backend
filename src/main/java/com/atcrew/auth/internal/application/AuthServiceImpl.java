package com.atcrew.auth.internal.application;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.RegisterCommand;
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
import com.atcrew.member.RegisterMemberCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final FirebaseVerifier firebaseVerifier;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    AuthServiceImpl(FirebaseVerifier firebaseVerifier, MemberService memberService,
            JwtProvider jwtProvider, RefreshTokenRepository refreshTokenRepository) {
        this.firebaseVerifier = firebaseVerifier;
        this.memberService = memberService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public AuthInfo login(String firebaseIdToken) {
        FirebaseUser firebaseUser = firebaseVerifier.verify(firebaseIdToken);
        String email = firebaseUser.email();
        AuthProvider tokenProvider = firebaseUser.provider();

        // 활성 회원 조회 — 실패 원인은 서버 로그에만 기록, 클라이언트에는 단일 에러 반환
        // (계정 존재 여부·탈퇴 이력·가입 방식을 응답에 노출하면 enumeration 공격에 악용될 수 있음)
        if (!memberService.existsByLoginEmail(email)) {
            String detail = memberService.isDeactivatedEmail(email) ? "탈퇴_계정" : "미가입_이메일";
            log.warn("로그인 실패[{}]: email={}", detail, LogMask.email(email));
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }

        MemberInfo member = memberService.findByLoginEmail(email);

        // 가입 경로 불일치 — 이유를 클라이언트에 노출하지 않고 로그에만 기록
        if (member.authProvider() != null && member.authProvider() != tokenProvider) {
            log.warn("로그인 실패[provider_불일치]: email={} registered={} attempted={}",
                    LogMask.email(email), member.authProvider(), tokenProvider);
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }

        member = memberService.recordLogin(member.id());

        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("로그인 성공: memberId={} email={} provider={}", member.id(), LogMask.email(email), tokenProvider);
        return new AuthInfo(accessToken, refreshTokenValue, member, false);
    }

    @Override
    @Transactional
    public AuthInfo register(RegisterCommand command) {
        FirebaseUser firebaseUser = firebaseVerifier.verify(command.firebaseIdToken());
        String email = firebaseUser.email();

        RegisterMemberCommand memberCommand = new RegisterMemberCommand(
                email,
                email.split("@")[0],
                firebaseUser.provider(),
                command.accountType(),
                command.companyName(),
                command.agreePrivacy(),
                command.agreeService(),
                command.agreeMarketing()
        );

        MemberInfo member = memberService.register(memberCommand);

        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = issueRefreshToken(member.id());

        log.info("회원가입 성공: memberId={} email={} accountType={} provider={}",
                member.id(), LogMask.email(email), command.accountType(), firebaseUser.provider());
        return new AuthInfo(accessToken, refreshTokenValue, member, true);
    }

    @Override
    @Transactional
    public AuthInfo refresh(String refreshToken) {
        // validate-then-delete: 만료/타입 오류 시 DB 항목이 소실되는 것을 방지
        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // findAndDelete 원자 연산 — 동시 요청이 와도 하나만 토큰을 가져감 (TOCTOU 방지)
        RefreshToken stored = refreshTokenRepository.findAndDeleteByTokenValue(refreshToken)
                .orElseThrow(() -> {
                    log.warn("refresh token 미존재 또는 재사용 시도 — 탈취 가능성");
                    return new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });

        MemberInfo member;
        try {
            member = memberService.findById(stored.getMemberId());
        } catch (DomainException e) {
            // MEMBER_DEACTIVATED(403)는 INVALID_REFRESH_TOKEN으로 둔갑하지 않도록 재전파
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                throw e;
            }
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Refresh Token Rotation: 신규 발급 (기존은 findAndDelete로 이미 제거됨)
        String newAccessToken = jwtProvider.generateAccessToken(member.id(), member.loginEmail());
        String newRefreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), newRefreshTokenValue, jwtProvider.getRefreshExpiry()));

        member = memberService.recordLogin(member.id());

        log.info("토큰 갱신: memberId={}", member.id());
        return new AuthInfo(newAccessToken, newRefreshTokenValue, member, false);
    }

    private String issueRefreshToken(String memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
        String refreshTokenValue = jwtProvider.generateRefreshToken(memberId);
        refreshTokenRepository.save(RefreshToken.of(memberId, refreshTokenValue, jwtProvider.getRefreshExpiry()));
        return refreshTokenValue;
    }
}
