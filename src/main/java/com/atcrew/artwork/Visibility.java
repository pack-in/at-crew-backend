package com.atcrew.artwork;

/**
 * 작품 피드 공개 여부 (마이페이지_작가-R04, 업로드-R09).
 *
 * <p>제3자 열람 가능 여부는 이 값 하나로 정해지지 않는다 — "피드 공개 여부 × 라이브 포트폴리오
 * (작가 페이지·최신 반영형) 편입 여부" 2요소로 계산한다({@code Artwork.accessFor}). 따라서 이 enum은
 * 피드 공개 ON/OFF 2값이며 "링크 공개"라는 제3의 상태는 존재하지 않는다.
 *
 * <p>라이트(Laiteu) 호환용 레거시 값 {@code LINK_ONLY}는 2026-10-20에 제거했다. 남아 있던 행은 V54가
 * {@code PRIVATE}로 옮겼다 — 판정상 이미 PRIVATE와 같았으므로 동작 변화는 없다.
 */
public enum Visibility {
    PUBLIC,      // 작품 피드 공개 ON — 홈 피드·검색·태그 탐색·상세 URL 모두 허용
    PRIVATE      // 작품 피드 공개 OFF — 라이브 포트폴리오에 편입된 경우에만 제3자 열람 허용
}
