package com.atcrew.company;

// TODO: 피그마 수정페이지에서 활동 지역 편집 UI의 정확한 옵션 값을 특정하지 못해
// member.ActiveRegion과 동일한 값 집합으로 우선 정의한다 — 실제 화면 확인 시 조정
// (docs/design/company-profile-module-design.md §3, §9).
public enum ActiveRegion {
    SEOUL,    // 서울
    GYEONGGI, // 경기도
    DAEJEON,  // 대전
    DAEGU,    // 대구
    GWANGJU,  // 광주
    BUSAN,    // 부산
    OTHER     // 기타
}
