package com.atcrew.recruit;

// 급여 지급 기준 단위 (laiteu PaymentUnit 참고) — JobPaymentType에 따라 선택 가능한 값이 다름
public enum JobPaymentUnit {
    // 연봉제(ANNUAL_SALARY)
    ANNUAL,          // 연봉
    MONTHLY,         // 월급

    // 고료제(PIECE_RATE)
    PER_CUT,         // 컷당
    PER_EPISODE,     // 회당
    PER_PAGE,        // 장당

    // MG+RS(MG_RS)
    PER_EPISODE_MG,  // 회당 MG
    MONTHLY_MG       // 월 MG
}
