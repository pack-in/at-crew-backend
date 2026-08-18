package com.atcrew.billing;

/**
 * 구독 상태. 결제 실패는 유예 기간 없이 즉시 스타터 혜택으로 내려간다(설정-R03) —
 * 따라서 PAST_DUE는 "프로 혜택 없음 + 복구 대기" 상태이며 프로로 취급하지 않는다.
 */
public enum SubscriptionStatus {

    ACTIVE,
    PAST_DUE,
    CANCELED;

    public boolean grantsPro() {
        return this == ACTIVE;
    }
}
