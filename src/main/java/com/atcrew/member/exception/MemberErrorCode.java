package com.atcrew.member.exception;

import org.springframework.http.HttpStatus;

public enum MemberErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    DUPLICATE_HANDLE(HttpStatus.CONFLICT, "이미 사용 중인 핸들입니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    CAREER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경력입니다");

    private final HttpStatus status;
    private final String message;

    MemberErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
