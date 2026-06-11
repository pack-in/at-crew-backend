package com.atcrew.member.internal.exception;

import org.springframework.http.HttpStatus;

public enum MemberErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    DUPLICATE_HANDLE(HttpStatus.CONFLICT, "이미 사용 중인 핸들입니다"),
    DUPLICATE_MEMBER_INFO(HttpStatus.CONFLICT, "이미 사용 중인 이메일 또는 핸들입니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    MEMBER_DEACTIVATED(HttpStatus.FORBIDDEN, "이미 탈퇴한 회원입니다"),
    CAREER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경력입니다"),
    CAREER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "경력은 최대 50개까지 등록할 수 있습니다"),
    INVALID_SLOT_COUNT(HttpStatus.BAD_REQUEST, "작업 가능 슬롯은 전체 슬롯보다 클 수 없습니다"),
    INVALID_CAREER_PERIOD(HttpStatus.BAD_REQUEST, "경력 종료일이 시작일보다 앞서거나 누락되었습니다"),
    HANDLE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "핸들 자동 생성에 실패했습니다. 잠시 후 다시 시도해주세요"),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다"),
    COMPANY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "기업 계정은 기업명이 필요합니다"),
    INVALID_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "인증 제공자 정보가 유효하지 않습니다");

    private final HttpStatus status;
    private final String message;

    MemberErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
