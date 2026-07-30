package com.atcrew.member;

/**
 * 프로필 검색 정렬 기준.
 *
 * <p>피그마 "작가 찾아보기" 화면은 조회순 정렬도 요구하지만, 프로필 조회수 집계가
 * 아직 없어 이번 구현 범위에서는 제외한다 (docs/design/community-module-design.md §5.3 참고).
 */
public enum ProfileSort {
    RECENTLY_UPDATED, // 최신 업데이트순 (기본값)
    EXPERIENCE        // 경력순 (신입 → 10년차 이상 순으로 내림차순)
}
