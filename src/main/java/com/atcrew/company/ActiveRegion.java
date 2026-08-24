package com.atcrew.company;

// TODO: 이 enum과 Company.activeRegions는 제거 대상이다.
//  피그마 UI개편_마이페이지_기업_수정페이지(5779:32101)에 활동 지역 항목이 아예 없고,
//  기획서 마이페이지_기업-R07("활동 지역 항목은 기업 프로필에서 제외")·정책 데이터구조-R03
//  ("활동 지역은 기업 프로필·구인글에 존재하지 않으며 팀원 모집글에서만 사용")도 같은 결론이다.
//  제거는 공개 API 필드 삭제라 별도 결정이 필요해 이번 변경 범위에서 보류한다.
//  값 집합도 member.ActiveRegion(도 단위 10종)과 이미 어긋나 있다 — 유지한다면 함께 맞춰야 한다.
public enum ActiveRegion {
    SEOUL,    // 서울
    GYEONGGI, // 경기도
    DAEJEON,  // 대전
    DAEGU,    // 대구
    GWANGJU,  // 광주
    BUSAN,    // 부산
    OTHER     // 기타
}
