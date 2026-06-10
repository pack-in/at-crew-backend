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
        MemberInfo member = isNewUser
                ? memberService.registerViaOAuth(email, email.split("@")[0])
                : memberService.findByLoginEmail(email);

        String accessToken = jwtProvider.generateAccessToken(member.id(), email);
        String refreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), refreshTokenValue, jwtProvider.getRefreshExpiry()));

        return new AuthInfo(accessToken, refreshTokenValue, member, isNewUser);
    }

    @Override
    @Transactional
    public AuthInfo refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenValue(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!jwtProvider.validateToken(refreshToken)) {
            refreshTokenRepository.delete(stored);
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        MemberInfo member;
        try {
            member = memberService.findById(stored.getMemberId());
        } catch (DomainException e) {
            // 회원이 탈퇴·삭제된 경우 토큰 무효화 후 401 반환
            refreshTokenRepository.delete(stored);
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Refresh Token Rotation: 기존 삭제 후 새로 발급
        refreshTokenRepository.delete(stored);
        String newAccessToken = jwtProvider.generateAccessToken(member.id(), member.loginEmail());
        String newRefreshTokenValue = jwtProvider.generateRefreshToken(member.id());
        refreshTokenRepository.save(
                RefreshToken.of(member.id(), newRefreshTokenValue, jwtProvider.getRefreshExpiry()));

        return new AuthInfo(newAccessToken, newRefreshTokenValue, member, false);
    }
}
