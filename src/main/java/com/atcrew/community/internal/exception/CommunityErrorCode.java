package com.atcrew.community.internal.exception;

import org.springframework.http.HttpStatus;

public enum CommunityErrorCode {

    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 배너입니다"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다");

    private final HttpStatus status;
    private final String message;

    CommunityErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
