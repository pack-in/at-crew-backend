package com.atcrew.billing.internal.domain;

/** 잔량 변동 사유. */
public enum LedgerReason {

    /** 결제 완료로 지급 */
    PURCHASE,
    /** 게시 성공으로 차감 */
    CONSUME,
    /** 환불되어 회수 */
    REFUND_REVOKE
}
