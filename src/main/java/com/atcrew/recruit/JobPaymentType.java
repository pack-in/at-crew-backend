package com.atcrew.recruit;

// 급여 지급 방식 (laiteu PaymentType 참고) — 값에 따라 선택 가능한 JobPaymentUnit이 달라짐
public enum JobPaymentType {
    ANNUAL_SALARY,  // 연봉제 → 지급 단위: ANNUAL/MONTHLY
    PIECE_RATE,     // 고료제 → 지급 단위: PER_CUT/PER_EPISODE/PER_PAGE
    MG_RS           // MG+RS → 지급 단위: PER_EPISODE_MG/MONTHLY_MG
}
