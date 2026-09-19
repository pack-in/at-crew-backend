package com.atcrew.auth.internal.infra.google;

public interface GoogleTokenVerifierPort {

    /**
     * Google ID Token을 검증하고 사용자 정보를 반환합니다.
     *
     * @throws com.atcrew.auth.internal.exception.AuthException 토큰이 유효하지 않을 때
     */
    GoogleUser verify(String idToken);
}
