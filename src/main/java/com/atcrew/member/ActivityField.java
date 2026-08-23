package com.atcrew.member;

/**
 * 작가 활동 분야 — 프로필에서 <b>복수 선택</b>한다(피그마 UI개편_마이페이지_작가_수정페이지 4971:25431
 * "활동 분야 (복수 선택 가능)").
 *
 * <p>정책 데이터구조-R03에 따라 작가·기업이 같은 코드 그룹을 공유하며, 값 이름은 작품 분야
 * (artwork 모듈 ArtworkField)와 동일하게 맞춘다 — 홈 화면이 작품 탭과 작가 탭에
 * 같은 칩 행을 쓰기 때문이다(기획서 홈-R01).
 */
public enum ActivityField {
    ILLUSTRATION, // 일러스트
    WEBTOON,      // 웹툰
    PRINT_COMIC,  // 출판만화
    ANIMATION     // 애니메이션
}
