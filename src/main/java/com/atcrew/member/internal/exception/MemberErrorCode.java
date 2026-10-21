package com.atcrew.member.internal.exception;

import org.springframework.http.HttpStatus;

public enum MemberErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    DUPLICATE_HANDLE(HttpStatus.CONFLICT, "이미 사용 중인 사용자 아이디예요."),
    DUPLICATE_MEMBER_INFO(HttpStatus.CONFLICT, "이미 사용 중인 이메일 또는 핸들입니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
    MEMBER_DEACTIVATED(HttpStatus.FORBIDDEN, "이미 탈퇴한 회원입니다"),
    CAREER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경력입니다"),
    CAREER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "경력은 최대 50개까지 등록할 수 있습니다"),
    INVALID_SLOT_COUNT(HttpStatus.BAD_REQUEST, "작업 가능 슬롯은 전체 슬롯보다 클 수 없습니다"),
    INVALID_CAREER_PERIOD(HttpStatus.BAD_REQUEST, "경력 종료일이 시작일보다 앞서거나 누락되었습니다"),
    CAREER_DATE_IN_FUTURE(HttpStatus.BAD_REQUEST, "작업 시작일·종료일은 미래 날짜일 수 없습니다"),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다"),
    INVALID_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "인증 제공자 정보가 유효하지 않습니다"),
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "유효하지 않은 시간대입니다"),
    INVALID_COUNTRY(HttpStatus.BAD_REQUEST, "유효하지 않은 국가 코드입니다"),
    INVALID_CUSTOM_TAG(HttpStatus.BAD_REQUEST, "직접입력 값은 최대 10자까지 입력할 수 있습니다"),
    PRIMARY_LANGUAGE_REQUIRED(HttpStatus.BAD_REQUEST, "주 사용 언어를 선택해야 합니다"),
    // 설정-R14 토스트 문구 그대로 — 프론트가 그대로 노출한다
    PRIMARY_LANGUAGE_CANNOT_BE_REMOVED(HttpStatus.BAD_REQUEST, "주 사용 언어로 설정된 게시물 언어는 해제할 수 없어요."),
    // 사용자 아이디(handle) 규칙 위반·변경 — 메시지는 명세 토스트 문구 그대로 (member-handle-design §1.2, §1.3)
    HANDLE_REQUIRED(HttpStatus.BAD_REQUEST, "사용자 아이디를 입력해주세요."),
    HANDLE_UNCHANGED(HttpStatus.BAD_REQUEST, "새 사용자 아이디를 입력해주세요."),
    HANDLE_TOO_SHORT(HttpStatus.BAD_REQUEST, "사용자 아이디는 4자 이상 입력해주세요."),
    HANDLE_TOO_LONG(HttpStatus.BAD_REQUEST, "사용자 아이디는 14자 이하로 입력해주세요."),
    HANDLE_INVALID_CHARACTERS(HttpStatus.BAD_REQUEST, "사용자 아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있어요."),
    HANDLE_CONSECUTIVE_UNDERSCORES(HttpStatus.BAD_REQUEST, "밑줄(_)은 연속으로 사용할 수 없어요."),
    HANDLE_RESERVED(HttpStatus.BAD_REQUEST, "사용할 수 없는 사용자 아이디예요. 다른 아이디를 입력해주세요."),
    HANDLE_CHANGE_TOO_SOON(HttpStatus.CONFLICT, "30일 이내에는 사용자 아이디를 다시 변경할 수 없어요."),
    HANDLE_CHANGE_CONFLICT(HttpStatus.CONFLICT, "사용자 아이디를 변경하지 못했어요. 잠시 후 다시 시도해주세요."),
    MEMBER_WITHDRAWN(HttpStatus.GONE, "탈퇴한 사용자예요.");

    private final HttpStatus status;
    private final String message;

    MemberErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
