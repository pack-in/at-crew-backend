package com.atcrew.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"), // 유효한 인증 정보 없음
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");        // 인증은 됐지만 권한 없음

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
