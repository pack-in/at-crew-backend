package com.atcrew.community.internal.exception;

import org.springframework.http.HttpStatus;

public enum CommunityErrorCode {

    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 배너입니다"),
    INVALID_SIZE(HttpStatus.BAD_REQUEST, "size는 0 이상이어야 합니다"),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "page는 1 이상이어야 하며 조회 상한을 넘을 수 없습니다");

    private final HttpStatus status;
    private final String message;

    CommunityErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
