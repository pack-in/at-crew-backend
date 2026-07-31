package com.atcrew.recruit;

// 지원자 채용 단계 관리 상태(laiteu 대응 없음, Figma 지원자 관리 UI 근거, 설계 §2.5)
public enum ApplicationReviewStatus {
    RECEIVED,   // 접수 (기본값)
    REVIEWING,  // 검토중
    ACCEPTED,   // 합격
    REJECTED    // 불합격
}
