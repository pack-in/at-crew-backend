package com.atcrew.member;

/**
 * 작업 스타일 — 단일 선택 (기획서 마이페이지_작가-R23 "작업 스타일(3)").
 *
 * <p>recruit 모듈의 {@code WorkStyle}과 축이 다르다(그쪽은 독립적/협업/체계적/유연 4종).
 * 이름을 WorkStyle로 두면 의미가 같다고 오해할 소지가 있어 WorkPace로 구분한다.
 */
public enum WorkPace {
    QUALITY_FIRST, // 완성도 중심
    SPEED_FIRST,   // 속도 우선
    PER_PROJECT    // 작업별 조율
}
