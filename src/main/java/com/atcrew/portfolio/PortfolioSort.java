package com.atcrew.portfolio;

public enum PortfolioSort {
    OLDEST,   // 오래된순 — 생성일 오름차순
    LATEST,   // 최신순 — 생성일 내림차순(기본값)
    UPDATED   // 업데이트순 — [수정하기]로 저장한 시각(lastEditedAt) 내림차순(마이페이지_작가-R37)
}
