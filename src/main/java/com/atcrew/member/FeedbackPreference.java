package com.atcrew.member;

/**
 * 선호 피드백 방식 — 복수 선택 (기획서 마이페이지_작가-R24 "선호 피드백 방식(7)").
 *
 * <p>recruit 모듈의 {@code FeedbackStyle}과 이름은 비슷하지만 축 자체가 다르다
 * (그쪽은 상세한/최소한의/실시간/정기적 4종). 두 값 집합의 통일은 별도 과제다.
 */
public enum FeedbackPreference {
    SPECIFIC,      // 구체적
    AUTONOMOUS,    // 자율적
    DIRECT,        // 직설적
    GENTLE,        // 부드러운
    AT_ONCE,       // 한번에
    FREQUENT,      // 자잘하게
    NO_PREFERENCE  // 상관없음
}
