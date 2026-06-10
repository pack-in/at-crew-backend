package com.atcrew.auth.internal.application;

import com.atcrew.auth.AuthInfo;
import com.atcrew.auth.AuthService;
import com.atcrew.auth.exception.AuthErrorCode;
import com.atcrew.auth.exception.AuthException;
import com.atcrew.auth.internal.domain.RefreshToken;
import com.atcrew.auth.internal.firebase.FirebaseVerifier;
import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import com.atcrew.common.DomainException;
import com.atcrew.common.security.JwtProvider;
import com.atcrew.common.LogMask;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
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
        String email = firebaseVerifier.verifyAndGetEmail(firebaseIdToken);

        boolean isNewUser = !memberService.existsByLoginEmail(email);
        MemberInfo member;
        if (isNewUser) {
            try {
                member = memberService.registerViaOAuth(email, email.split("@")[0]);
            } catch (DomainException e) {
                if (!memberService.existsByLoginEmail(email)) {
                    throw e;
                }
                // 동시 첫 로그인 경쟁: 다른 요청이 먼저 등록 완료 → 기존 회원으로 진행
                isNewUser = false;
                member = memberService.findByLoginEmail(email);
            }
        } else {
            member = memberService.findByLoginEmail(email);
        }

        // F4: recordLogin을 토큰 발급 전에 호출 — 비활성 회원이면 내부 assertActive에서 MEMBER_DEACTIVATED 발생
        // F6: 반환된 최신 MemberInfo로 lastLoginAt 갱신 반영
        member = memberService.recordLogin(member.id());

        // 이전 세션 토큰 정리 — 디바이스 1개 정책, DB 무기한 누적 방지
        refreshTokenRepository.deleteAllByMemberId(member.id());
        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), refreshTokenValue, jwtProvider.getRefreshExpiry()));

        log.info("로그인 성공: memberId={} email={} isNewUser={}", member.id(), LogMask.email(email), isNewUser);
        return new AuthInfo(accessToken, refreshTokenValue, member, isNewUser);
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
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        MemberInfo member;
        try {
            member = memberService.findById(stored.getMemberId());
        } catch (DomainException e) {
            // F1: MEMBER_DEACTIVATED(403)는 INVALID_REFRESH_TOKEN으로 둔갑하지 않도록 재전파
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

        // F5: 토큰 갱신 시에도 lastLoginAt 업데이트 (앱 상시 구동 사용자 MAU 정확도)
        member = memberService.recordLogin(member.id());

        log.info("토큰 갱신: memberId={}", member.id());
        return new AuthInfo(newAccessToken, newRefreshTokenValue, member, false);
    }
}
