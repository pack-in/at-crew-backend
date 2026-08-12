package com.atcrew.artwork;

/**
 * 작품 피드 공개 여부 (마이페이지_작가-R04, 업로드-R09).
 *
 * <p>제3자 열람 가능 여부는 이 값 하나로 정해지지 않는다 — "피드 공개 여부 × 라이브 포트폴리오
 * (작가 페이지·최신 반영형) 편입 여부" 2요소로 계산한다({@code Artwork.accessFor}). 따라서 이 enum은
 * 사실상 피드 공개 ON/OFF 2값이며 "링크 공개"라는 제3의 상태는 존재하지 않는다.
 */
public enum Visibility {
    PUBLIC,      // 작품 피드 공개 ON — 홈 피드·검색·태그 탐색·상세 URL 모두 허용

    /**
     * 링크 공개 — 라이트(Laiteu) {@code ArtworkStatus}에 실존하는 값이라 ETL 매핑 대상으로만 남겨둔다.
     * 판정상 {@link #PRIVATE}와 동일하게 취급하며, 신규 쓰기 경로(업로드·공개 상태 변경)에서는 400으로 막는다.
     * enum 상수 물리 제거는 라이트 마이그레이션 완료 후 별도 과제다(docs/roadmap.md).
     *
     * @deprecated 2요소 공개 모델에서 의미가 없는 값이다. 신규 코드에서 쓰지 않는다.
     */
    @Deprecated
    LINK_ONLY,   // 링크 공개(라이트 호환 레거시) — PRIVATE와 동일 취급

    PRIVATE      // 작품 피드 공개 OFF — 라이브 포트폴리오에 편입된 경우에만 제3자 열람 허용
}
