package com.atcrew.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase 토큰이 유효하지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다"),
    FIREBASE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Firebase가 설정되지 않았습니다");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
