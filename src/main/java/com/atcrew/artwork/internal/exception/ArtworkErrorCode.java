package com.atcrew.artwork.internal.exception;

import org.springframework.http.HttpStatus;

public enum ArtworkErrorCode {

    ARTWORK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 작품입니다"),
    ARTWORK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작품에 접근 권한이 없습니다"),
    ARTWORK_DELETED(HttpStatus.GONE, "삭제된 작품입니다"),
    ARTWORK_PRIVATE(HttpStatus.FORBIDDEN, "비공개 작품입니다"),
    ARTWORK_BLOCKED(HttpStatus.GONE, "운영 정책에 따라 열람할 수 없는 작품입니다"),
    ARTWORK_NOT_READY(HttpStatus.BAD_REQUEST, "이미지 처리 중인 작품은 이 작업을 수행할 수 없습니다"),
    ARTWORK_NOT_DELETED(HttpStatus.BAD_REQUEST, "휴지통에 있는 작품이 아닙니다"),
    // 스타터 플랜 제한(마이페이지_작가-R20) — 프로 플랜으로 전환하면 해제된다. 휴지통 복구에도 동일 적용(휴지통-R03)
    STARTER_ARTWORK_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "스타터 플랜은 작품을 최대 4개까지 등록할 수 있습니다"),
    // 다중 언어 노출은 프로 전용(업로드-R30, REQ-020). 문구는 정본 토스트 그대로다
    MULTI_LANGUAGE_REQUIRES_PRO(HttpStatus.FORBIDDEN, "다중 언어 노출은 유료 기능이에요."),
    // 주 사용 언어는 플랜과 무관하게 반드시 포함한다 — 프로의 다중 선택은 "추가"지 "대체"가 아니다
    // (요금제-R04 "여러 활동 언어로 노출 확대", 설정-R14의 주 언어 해제 금지와 같은 결)
    LANGUAGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "작품 언어에는 주 사용 언어를 반드시 포함해야 합니다"),
    INVALID_LANGUAGE_COUNT(HttpStatus.BAD_REQUEST, "게시물 언어는 1개 이상 4개 이하로 선택해야 합니다"),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지 수는 1개 이상 30개 이하여야 합니다"),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 이미지 형식입니다. jpeg, png, webp만 가능합니다"),
    INVALID_REPRESENTATIVE_INDEX(HttpStatus.BAD_REQUEST, "대표 이미지 인덱스가 유효하지 않습니다"),
    BOOKMARK_FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 북마크 폴더입니다"),
    BOOKMARK_FOLDER_DUPLICATE_NAME(HttpStatus.CONFLICT, "이미 존재하는 폴더명입니다"),
    BOOKMARK_FOLDER_NAME_BLANK(HttpStatus.BAD_REQUEST, "폴더명은 공백만 입력할 수 없습니다"),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 작품입니다"),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다"),
    PRESIGN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "업로드 URL 생성에 실패했습니다"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다");

    private final HttpStatus status;
    private final String message;

    ArtworkErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
