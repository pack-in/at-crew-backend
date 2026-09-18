package com.atcrew.auth.internal.infra.google;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.member.AuthProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import java.io.IOException;
import java.security.GeneralSecurityException;

class GoogleTokenVerifierPortImpl implements GoogleTokenVerifierPort {

    private final GoogleIdTokenVerifier verifier;

    GoogleTokenVerifierPortImpl(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleUser verify(String idToken) {
        GoogleIdToken decoded;
        try {
            // 서명·만료·iss·aud를 라이브러리가 함께 검증한다. 어느 하나라도 어긋나면 null이 반환된다.
            decoded = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException e) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN, e);
        }
        if (decoded == null) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }

        String email = decoded.getPayload().getEmail();
        if (email == null) {
            // 이메일 scope 없이 발급된 토큰 — 앳크루는 이메일 기반 가입만 지원
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }
        return new GoogleUser(email, AuthProvider.GOOGLE, Boolean.TRUE.equals(decoded.getPayload().getEmailVerified()));
    }
}
