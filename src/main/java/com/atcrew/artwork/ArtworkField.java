package com.atcrew.artwork;

public enum ArtworkField {
    ILLUSTRATION, WEBTOON,
    // 값 집합은 홈 화면(6107:24822)의 출판만화 쪽으로 확정됐다(PM, 2026-08-27).
    // 검색 화면(5752:29315)의 웹소설은 구버전 기획이라 폐기. ETC는 홈 칩에는 없지만 업로드 분류용으로 유지한다.
    PRINT_COMIC, ANIMATION, ETC
}
