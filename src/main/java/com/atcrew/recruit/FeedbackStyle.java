package com.atcrew.recruit;

// 구직자가 선호하는 피드백 방식 (laiteu 대응 없음 — JobSeekingPost 신규 엔티티, 설계 §2.3 기준 합리적 값)
public enum FeedbackStyle {
    DETAILED,       // 상세한 피드백 선호
    MINIMAL,        // 최소한의 피드백 선호
    REAL_TIME,      // 작업 중 실시간 피드백 선호
    PERIODIC        // 정기적인 단계별 피드백 선호
}
