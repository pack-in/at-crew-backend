package com.atcrew.artwork.internal.domain.view;

/**
 * 유효 열람 판정 결과(홈-R14). 24시간 안의 반복 열람은 유효 열람이 아니라 기록 자체를 남기지 않는다.
 */
public enum ArtworkViewKind {
    FIRST,   // 이 열람자의 최초 열람
    REVISIT  // 직전 집계로부터 24시간이 지난 재방문
}
