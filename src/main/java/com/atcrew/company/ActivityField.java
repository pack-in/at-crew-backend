package com.atcrew.company;

/**
 * 기업 활동 분야 — 기업 프로필에서 <b>단일 선택</b>한다(피그마 UI개편_마이페이지_기업_수정페이지
 * 5779:32101, 기획서 마이페이지_기업-R07 "활동 분야(4)는 단일 칩").
 *
 * <p>값 집합은 {@code member.ActivityField}와 동일해야 하지만(정책 데이터구조-R03 "활동 분야는
 * 작가·기업이 동일 코드 그룹을 공유한다"), 모듈 간 직접 의존 금지 원칙에 따라 company 모듈이
 * 자체 정의한다(docs/design/company-profile-module-design.md §3).
 */
public enum ActivityField {
    ILLUSTRATION, // 일러스트
    WEBTOON,      // 웹툰
    PRINT_COMIC,  // 출판만화
    ANIMATION     // 애니메이션
}
