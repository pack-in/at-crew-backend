package com.atcrew.search;

import java.util.List;

/**
 * 검색 결과 응답 봉투. 공용 {@code common.response.CursorPage}에 없는 {@code totalCount}가 필요해
 * (피그마 "검색 결과 수" 영역, docs/design/search-module-design.md §1.5) 검색 모듈이 자체 소유한다.
 */
public record SearchPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext,
        long totalCount
) {
    public static <T> SearchPage<T> of(List<T> items, String nextCursor, long totalCount) {
        return new SearchPage<>(items, nextCursor, nextCursor != null, totalCount);
    }

    public static <T> SearchPage<T> empty() {
        return new SearchPage<>(List.of(), null, false, 0);
    }
}
