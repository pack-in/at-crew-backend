package com.atcrew.billing;

/**
 * 회원의 현재 요금제. 스타터는 전 가입자 기본값이며 결제가 없다(요금제-R02).
 */
public enum PlanType {

    STARTER,
    PRO_MONTHLY,
    PRO_YEARLY;

    public boolean isPro() {
        return this != STARTER;
    }
}
