package com.atcrew.recruit;

import java.time.Instant;
import java.util.List;

/**
 * recruit 3종(구인글·구직글·팀원모집글) 통합 검색 조건. 공개 상태(PUBLISHED)만 검색 대상이다.
 *
 * <p>커서는 (createdAt, id) keyset 방식이며 recruit은 구조화된 값만 받는다 — 커서 문자열 인코딩은
 * 호출자(search 모듈)가 소유한다. 두 값이 모두 있을 때만 커서가 적용된다.
 *
 * <p>태그 필터(roles·genres)는 축 내부 OR, 축 간 AND로 결합한다. recruit의 태그는 작성자가 직접 입력하는
 * 자유 문자열이라 정본 태그 목록이 확정되기 전까지는 문자열 동일 비교로 매칭한다
 * (docs/design/search-module-design.md §9-2).
 */
public record RecruitSearchQuery(
        String q,                        // 검색어 — 제목 부분 일치 (null/공백이면 제목 조건 없음)
        List<RecruitPostType> postTypes, // 검색 대상 게시글 유형 (null/empty면 3종 전체)
        List<String> roles,              // 담당 업무 태그 필터 (null/empty면 조건 없음)
        List<String> genres,             // 장르 태그 필터 (null/empty면 조건 없음)
        Instant cursorCreatedAt,         // 커서 — 이 작성 시각보다 과거 항목부터 조회 (null이면 첫 페이지)
        String cursorId,                 // 커서 tie-breaker — 작성 시각이 같으면 이 ID보다 큰 항목부터 조회
        int size                         // 페이지 크기
) {

    /** 지정한 유형이 검색 대상인지 여부 — postTypes가 비어 있으면 3종 전체가 대상이다. */
    public boolean includes(RecruitPostType postType) {
        return postTypes == null || postTypes.isEmpty() || postTypes.contains(postType);
    }
}
