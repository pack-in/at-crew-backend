package com.atcrew.auth.internal.firebase;

import com.atcrew.auth.exception.AuthErrorCode;
import com.atcrew.auth.exception.AuthException;
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
            return firebaseAuth.verifyIdToken(idToken).getEmail();
        } catch (FirebaseAuthException e) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
        }
    }
}
