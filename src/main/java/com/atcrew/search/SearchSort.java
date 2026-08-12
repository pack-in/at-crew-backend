package com.atcrew.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 결과 정렬 기준. 피그마 검색 화면에 명시적 정렬 UI가 확인되지 않아, 검색어 유무로 기본값을 결정한다
 * (docs/design/search-module-design.md §1.5).
 */
@Schema(description = """
        검색 결과 정렬 기준 — 미지정 시 검색어(q)가 있으면 RELEVANCE, 없으면 LATEST가 적용된다.
        - RELEVANCE: 관련도순. 동점은 최신순 → ID순으로 정렬한다.
        - LATEST: 최신순(등록 시각 내림차순).
        포트폴리오와 구인글/구직글/팀원모집글을 함께 검색하면 두 소스의 관련도 점수를 비교할 수 없어 \
        이 값과 무관하게 최신순으로 병합된다.""",
        example = "LATEST")
public enum SearchSort {
    RELEVANCE, // 관련도순 — 검색어(q)가 있을 때 기본값
    LATEST     // 최신순 — 검색어 없이 필터만 있을 때 기본값
}
