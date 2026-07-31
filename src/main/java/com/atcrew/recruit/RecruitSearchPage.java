package com.atcrew.recruit;

import java.util.List;

/**
 * recruit 검색 결과 페이지. 검색 결과 수 표시를 위해 전체 건수가 필요해 공용 {@code CursorPage} 대신 별도 타입을 쓴다.
 * 다음 커서는 마지막 항목의 (createdAt, id)로 호출자가 직접 만든다 — 커서 인코딩은 search 모듈이 소유한다.
 */
public record RecruitSearchPage(
        List<RecruitSearchResultInfo> items, // 검색 결과 (작성 시각 내림차순, 같은 시각이면 ID 오름차순)
        boolean hasNext,                     // 다음 페이지 존재 여부
        long totalCount                      // 조건에 맞는 전체 건수
) {

    public static RecruitSearchPage empty() {
        return new RecruitSearchPage(List.of(), false, 0);
    }
}
