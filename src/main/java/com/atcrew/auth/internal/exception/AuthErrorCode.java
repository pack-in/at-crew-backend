package com.atcrew.auth.internal.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    // 이메일 로그인 — 단일 실패 코드 (미가입·탈퇴·비밀번호 오류 통합, enumeration 방지)
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 다시 확인해주세요"),
    // 마이그레이션 회원 (passwordHash == null) — 428 사용, 4단계 후 제거 예정
    PASSWORD_RESET_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "비밀번호 재설정이 필요해요"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해주세요"),

    // Google 로그인 — Firebase 토큰이 이메일 소유를 증명하므로 404 노출이 enumeration 아님
    MEMBER_NOT_REGISTERED(HttpStatus.NOT_FOUND, "가입되지 않은 계정이에요. 회원가입을 진행해주세요"),
    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase 토큰이 유효하지 않습니다"),
    FIREBASE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Firebase가 설정되지 않았습니다"),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다"),

    // 토큰
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
