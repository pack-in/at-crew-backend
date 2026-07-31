package com.atcrew.artwork;

public enum ArtworkField {
    ILLUSTRATION, WEBTOON,
    // TODO: 피그마 UI개편_검색(5154:41768)에는 PRINT_COMIC(출판만화) 대신 웹소설(WEBNOVEL)이 있음.
    // 피그마가 기획 기준이므로 이 값을 WEBNOVEL로 교체(또는 추가)해야 함 — artwork 모듈 데이터 마이그레이션 필요.
    PRINT_COMIC, ANIMATION, ETC
}
