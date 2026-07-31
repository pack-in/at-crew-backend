package com.atcrew.search;

/**
 * 검색 결과 정렬 기준. 피그마 검색 화면에 명시적 정렬 UI가 확인되지 않아, 검색어 유무로 기본값을 결정한다
 * (docs/design/search-module-design.md §1.5).
 */
public enum SearchSort {
    RELEVANCE, // 관련도순 — 검색어(q)가 있을 때 기본값
    LATEST     // 최신순 — 검색어 없이 필터만 있을 때 기본값
}
