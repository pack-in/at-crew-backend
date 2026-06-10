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
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.exception.MemberException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthServiceImpl implements AuthService {

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
            } catch (MemberException e) {
                // 동시 첫 로그인 경쟁: 다른 요청이 먼저 등록 완료 → 기존 회원으로 진행
                isNewUser = false;
                member = memberService.findByLoginEmail(email);
            }
        } else {
            member = memberService.findByLoginEmail(email);
        }

        // 이전 세션 토큰 정리 — 디바이스 1개 정책, DB 무기한 누적 방지
        refreshTokenRepository.deleteAllByMemberId(member.id());
        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), refreshTokenValue, jwtProvider.getRefreshExpiry()));

        return new AuthInfo(accessToken, refreshTokenValue, member, isNewUser);
    }

    @Override
    @Transactional
    public AuthInfo refresh(String refreshToken) {
        // findAndDelete 원자 연산 — 동시 요청이 와도 하나만 토큰을 가져감 (TOCTOU 방지)
        RefreshToken stored = refreshTokenRepository.findAndDeleteByTokenValue(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        MemberInfo member;
        try {
            member = memberService.findById(stored.getMemberId());
        } catch (DomainException e) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Refresh Token Rotation: 신규 발급 (기존은 findAndDelete로 이미 제거됨)
        String newAccessToken = jwtProvider.generateAccessToken(member.id(), member.loginEmail());
        String newRefreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), newRefreshTokenValue, jwtProvider.getRefreshExpiry()));

        return new AuthInfo(newAccessToken, newRefreshTokenValue, member, false);
    }
}
