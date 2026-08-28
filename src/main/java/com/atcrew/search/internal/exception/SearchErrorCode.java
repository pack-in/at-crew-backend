package com.atcrew.search.internal.exception;

import org.springframework.http.HttpStatus;

public enum SearchErrorCode {

    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다"),
    INVALID_SIZE(HttpStatus.BAD_REQUEST, "size는 1 이상 50 이하여야 합니다"),
    UNSUPPORTED_SORT_FOR_MERGED_SEARCH(HttpStatus.BAD_REQUEST,
            "통합검색(postTypes 미지정)은 OLDEST 정렬을 지원하지 않습니다. postTypes를 지정해 단일 카테고리로 검색하세요"),
    INTERNAL_SECRET_INVALID(HttpStatus.UNAUTHORIZED, "내부 요청 인증에 실패했습니다"),
    REINDEX_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "재색인에 실패했습니다");

    private final HttpStatus status;
    private final String message;

    SearchErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
