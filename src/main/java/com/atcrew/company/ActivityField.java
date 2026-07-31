package com.atcrew.company;

// member.ActivityField와 의미는 같지만 모듈 간 직접 의존 금지 원칙에 따라 company 모듈이 자체 정의한다
// (docs/design/company-profile-module-design.md §3).
public enum ActivityField {
    ILLUSTRATION, // 일러스트
    WEBTOON,      // 웹툰
    ANIMATION,    // 애니메이션
    WEB_NOVEL,    // 웹소설
    OTHER         // 기타
}
