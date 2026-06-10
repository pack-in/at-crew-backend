package com.atcrew.auth.internal.firebase;

public interface FirebaseVerifier {

    /**
     * Firebase ID Token을 검증하고 이메일 주소를 반환합니다.
     *
     * @throws com.atcrew.auth.exception.AuthException 토큰이 유효하지 않을 때
     */
    String verifyAndGetEmail(String idToken);
}
