package com.atcrew.common;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String logDetail; // 응답 제외 — 로그 전용 (민감 정보 포함 가능)

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.logDetail = null;
    }

    public DomainException(HttpStatus status, String code, String message, String logDetail) {
        super(message);
        this.status = status;
        this.code = code;
        this.logDetail = logDetail;
    }

    public DomainException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.logDetail = null;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getLogDetail() { return logDetail; }
}
