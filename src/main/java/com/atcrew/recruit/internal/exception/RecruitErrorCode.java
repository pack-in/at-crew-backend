package com.atcrew.recruit.internal.exception;

import org.springframework.http.HttpStatus;

public enum RecruitErrorCode {

    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 구인글입니다"),
    TEAM_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀원모집글입니다"),
    JOB_SEEKING_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 구직글입니다"),
    FORBIDDEN_NOT_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 수행할 수 있는 작업입니다"),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "이미 지원한 공고입니다"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "허용되지 않는 상태 전이입니다"),
    INVALID_AMOUNT_RANGE(HttpStatus.BAD_REQUEST, "최소 금액은 최대 금액보다 클 수 없습니다"),
    INVALID_ACTIVITY_REGION(HttpStatus.BAD_REQUEST, "온라인 활동인 경우 활동 지역을 입력할 수 없습니다");

    private final HttpStatus status;
    private final String message;

    RecruitErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
