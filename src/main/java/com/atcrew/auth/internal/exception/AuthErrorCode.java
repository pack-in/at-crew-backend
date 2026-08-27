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

    // 비밀번호 변경 (설정 화면)
    // 이미 인증된 요청이므로 401이 아닌 400을 쓴다 — 401은 클라이언트 공통 인터셉터에서
    // 토큰 만료로 해석돼 로그아웃 처리될 수 있어 "현재 비밀번호 오답"과 의미가 어긋난다.
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않아요"),
    PASSWORD_CHANGE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호를 변경할 수 없어요"),

    // 토큰
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다"),

    // 비밀번호 재설정 (§7) — 요청 자체는 계정 존재 여부와 무관하게 항상 200이므로 여기 코드들은
    // confirm 단계에서만 발생한다.
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.UNAUTHORIZED, "재설정 링크가 유효하지 않거나 만료되었어요");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
