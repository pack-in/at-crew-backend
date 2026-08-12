package com.atcrew.billing;

public enum SubscriptionStatus {
    NONE,        // 구독 이력 없음 (스타터 기본값)
    ACTIVE,      // 정상 구독 중
    PAST_DUE,    // 결제 실패 — plan은 유지하되 게이팅은 스타터로 떨어진다
    CANCELED,    // 구독 취소 완료
    INCOMPLETE,  // 최초 결제 미완료
    TRIALING,    // 체험 기간 — 프로 권한 부여
    UNPAID       // 재시도 끝에 미납 확정
}
