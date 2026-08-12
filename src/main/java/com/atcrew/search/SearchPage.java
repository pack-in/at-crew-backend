package com.atcrew.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 검색 결과 응답 봉투. 공용 {@code common.response.CursorPage}에 없는 {@code totalCount}가 필요해
 * (피그마 "검색 결과 수" 영역, docs/design/search-module-design.md §1.5) 검색 모듈이 자체 소유한다.
 */
@Schema(description = "검색 결과 페이지 — 커서 페이지네이션 응답 봉투")
public record SearchPage<T>(
        @Schema(description = "이번 페이지의 결과 목록 — 결과가 없으면 빈 배열")
        List<T> items,

        @Schema(description = "다음 페이지 커서 — 다음 요청의 cursor에 그대로 전달한다. 마지막 페이지면 null",
                nullable = true, example = "MTc4NjQ5NjgzOTAwMF8wMTlmZjM4Mg")
        String nextCursor,

        @Schema(description = "다음 페이지 존재 여부 — false면 더 이상 조회하지 않는다", example = "false")
        boolean hasNext,

        @Schema(description = "조건에 일치하는 전체 결과 수(화면의 '검색 결과 N건' 표기용). "
                + "포트폴리오와 구인글/구직글/팀원모집글을 함께 검색하면 두 소스의 합계다", example = "0")
        long totalCount
) {
    public static <T> SearchPage<T> of(List<T> items, String nextCursor, long totalCount) {
        return new SearchPage<>(items, nextCursor, nextCursor != null, totalCount);
    }

    public static <T> SearchPage<T> empty() {
        return new SearchPage<>(List.of(), null, false, 0);
    }
}
