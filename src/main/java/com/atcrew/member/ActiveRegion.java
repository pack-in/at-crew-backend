package com.atcrew.member;

/**
 * 작가 활동 지역 — 프로필에서 <b>단일 선택</b>한다(피그마 UI개편_마이페이지_작가_수정페이지 4971:25431의
 * "활동 지역" 항목에는 복수 선택 라벨이 없다. 기획서 마이페이지_작가-R23도 "단일 칩: … 활동 지역(10)").
 *
 * <p>값 집합은 광역시가 아닌 도 단위 10개다 — 이전 값(대전·대구·광주·부산·기타)은 정본에서 빠졌다.
 */
public enum ActiveRegion {
    SEOUL,     // 서울
    GYEONGGI,  // 경기도
    GANGWON,   // 강원도
    CHUNGBUK,  // 충청북도
    CHUNGNAM,  // 충청남도
    JEONBUK,   // 전라북도
    JEONNAM,   // 전라남도
    GYEONGBUK, // 경상북도
    GYEONGNAM, // 경상남도
    JEJU       // 제주도
}
