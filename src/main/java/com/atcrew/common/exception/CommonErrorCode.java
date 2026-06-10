package com.atcrew.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"); // 유효한 인증 정보 없음

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
