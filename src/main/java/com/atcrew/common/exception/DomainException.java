package com.atcrew.common.exception;

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

    /**
     * 예외를 로그·분석 이벤트용 코드 문자열로 바꾼다. 도메인 예외는 {@link #getCode()}를,
     * 그 외(예상 못 한 런타임 예외)는 클래스명을 쓴다 — "UNKNOWN"으로 뭉개면 원인별 집계가 안 된다.
     */
    public static String codeOf(RuntimeException e) {
        return e instanceof DomainException de ? de.getCode() : e.getClass().getSimpleName();
    }
}
