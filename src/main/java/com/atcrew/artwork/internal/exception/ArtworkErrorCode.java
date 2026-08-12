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
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지 수는 1개 이상 20개 이하여야 합니다"),
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
