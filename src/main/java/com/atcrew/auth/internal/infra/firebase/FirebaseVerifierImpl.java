package com.atcrew.auth.internal.infra.firebase;

import com.atcrew.auth.internal.exception.AuthErrorCode;
import com.atcrew.auth.internal.exception.AuthException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

class FirebaseVerifierImpl implements FirebaseVerifier {

    private final FirebaseAuth firebaseAuth;

    FirebaseVerifierImpl(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public String verifyAndGetEmail(String idToken) {
        try {
            String email = firebaseAuth.verifyIdToken(idToken).getEmail();
            if (email == null) {
                // 전화번호·익명 계정은 이메일 없음 — 앳크루는 이메일 기반 가입만 지원
                throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
            }
            return email;
        } catch (FirebaseAuthException e) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN, e);
        }
    }
}
