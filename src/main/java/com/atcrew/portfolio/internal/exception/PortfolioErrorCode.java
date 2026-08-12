package com.atcrew.portfolio.internal.exception;

import org.springframework.http.HttpStatus;

public enum PortfolioErrorCode {

    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 포트폴리오입니다"),
    PORTFOLIO_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 포트폴리오에 접근 권한이 없습니다"),
    PORTFOLIO_BLOCKED(HttpStatus.GONE, "더 이상 열람할 수 없는 포트폴리오입니다"),
    SNAPSHOT_PORTFOLIO_IMMUTABLE(HttpStatus.CONFLICT, "고정형 포트폴리오는 수정할 수 없습니다"),
    ARTIST_PAGE_NOT_DELETABLE(HttpStatus.CONFLICT, "작가 페이지 포트폴리오는 삭제할 수 없습니다"),
    ARTIST_PAGE_TITLE_IMMUTABLE(HttpStatus.BAD_REQUEST, "작가 페이지 포트폴리오는 제목을 변경할 수 없습니다"),
    INVALID_PORTFOLIO_TITLE(HttpStatus.BAD_REQUEST, "포트폴리오 제목이 유효하지 않습니다"),
    PORTFOLIO_ITEM_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "포트폴리오에 담을 수 있는 작품 수를 초과했습니다"),
    SLUG_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "공유 링크 생성에 실패했습니다"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서 값입니다"),
    // 포트폴리오가 자체 판단해 던지는 코드 — 작품이 없거나, 삭제됐거나, 요청자 소유가 아닌 경우를 구분하지 않는다(§4).
    ARTWORK_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오에 담을 작품을 찾을 수 없습니다");

    private final HttpStatus status;
    private final String message;

    PortfolioErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
