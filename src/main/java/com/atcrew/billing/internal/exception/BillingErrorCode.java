package com.atcrew.billing.internal.exception;

import org.springframework.http.HttpStatus;

public enum BillingErrorCode {

    PRO_PLAN_REQUIRED(HttpStatus.FORBIDDEN, "프로 플랜에서만 사용할 수 있는 기능입니다");

    private final HttpStatus status;
    private final String message;

    BillingErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
