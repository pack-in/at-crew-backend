package com.atcrew.member;

/**
 * 프로필 검색 정렬 기준 — 기획서 홈-R02("작가찾기=업데이트순·조회순·경력순").
 */
public enum ProfileSort {
    RECENTLY_UPDATED, // 최신 업데이트순 (기본값)
    VIEW_COUNT,       // 조회순 (프로필 열람수 내림차순, 마이페이지_작가-R03)
    EXPERIENCE        // 경력순 (신입 → 10년차 이상 순으로 내림차순)
}
