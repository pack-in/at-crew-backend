package com.atcrew.billing;

public enum Plan {
    STARTER,      // 무료 기본 플랜 — 구독 레코드가 없는 회원도 이 값으로 취급한다
    PRO_MONTHLY,  // 프로 월간
    PRO_YEARLY    // 프로 연간
}
