package com.atcrew.auth.internal.infra.firebase;

public interface FirebaseVerifier {

    /**
     * Firebase ID Token을 검증하고 사용자 정보를 반환합니다.
     *
     * @throws com.atcrew.auth.internal.exception.AuthException 토큰이 유효하지 않을 때
     */
    FirebaseUser verify(String idToken);
}
