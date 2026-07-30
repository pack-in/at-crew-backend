package com.atcrew.company.internal.exception;

import org.springframework.http.HttpStatus;

public enum CompanyErrorCode {

    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 기업 프로필입니다"),
    COMPANY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 기업 프로필을 보유한 회원입니다"),
    COMPANY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 기업 프로필에 접근 권한이 없습니다"),
    CAREER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경력입니다"),
    CAREER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "경력은 최대 50개까지 등록할 수 있습니다"),
    INVALID_CAREER_PERIOD(HttpStatus.BAD_REQUEST, "경력 종료일이 시작일보다 앞서거나 누락되었습니다");

    private final HttpStatus status;
    private final String message;

    CompanyErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
