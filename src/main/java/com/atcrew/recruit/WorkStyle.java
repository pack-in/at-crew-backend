package com.atcrew.recruit;

// 구직자가 선호하는 작업 스타일 (laiteu 대응 없음 — JobSeekingPost 신규 엔티티, 설계 §2.3 기준 합리적 값)
public enum WorkStyle {
    INDEPENDENT,     // 독립적으로 작업하는 것을 선호
    COLLABORATIVE,   // 팀원들과 협업하는 것을 선호
    STRUCTURED,      // 체계적이고 정해진 프로세스를 선호
    FLEXIBLE         // 자유롭고 유연한 방식을 선호
}
