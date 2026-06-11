package com.atcrew.auth.internal.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase 토큰이 유효하지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다"),
    FIREBASE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Firebase가 설정되지 않았습니다"),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다"),
    // 미가입·탈퇴·provider 불일치를 하나로 통합 — 클라이언트에 계정 존재 여부·가입 방식을 노출하지 않음
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 로그인 방식을 다시 확인해주세요");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
