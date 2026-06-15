package com.atcrew.auth.internal.infra.firebase;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.atcrew.member.AuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import java.util.Map;

class FirebaseVerifierImpl implements FirebaseVerifier {

    private final FirebaseAuth firebaseAuth;

    FirebaseVerifierImpl(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public FirebaseUser verify(String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            String email = decoded.getEmail();
            if (email == null) {
                // 전화번호·익명 계정은 이메일 없음 — 앳크루는 이메일 기반 가입만 지원
                throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
            }
            return new FirebaseUser(email, extractProvider(decoded), decoded.isEmailVerified());
        } catch (FirebaseAuthException e) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN, e);
        }
    }

    private AuthProvider extractProvider(FirebaseToken decoded) {
        @SuppressWarnings("unchecked")
        Map<String, Object> firebaseClaim = (Map<String, Object>) decoded.getClaims().get("firebase");
        if (firebaseClaim == null) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
        }
        String signInProvider = (String) firebaseClaim.get("sign_in_provider");
        // Google 로그인 전용 — Firebase 이메일 계정 토큰 우회 경로 차단
        if (!"google.com".equals(signInProvider)) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
        return AuthProvider.GOOGLE;
    }
}
