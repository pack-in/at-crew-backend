package com.atcrew.billing.internal.exception;

import org.springframework.http.HttpStatus;

public enum BillingErrorCode {

    /** 단건 게시 상품 보유 개수가 없어 게시할 수 없다(요금제-R06, 구인구직-R02) — 프론트는 구매 유도 모달을 띄운다. */
    ENTITLEMENT_REQUIRED(HttpStatus.FORBIDDEN, "게시 권한을 구매해야 이용할 수 있습니다"),
    PRO_PLAN_REQUIRED(HttpStatus.FORBIDDEN, "프로 플랜에서만 이용할 수 있는 기능입니다"),
    /** 프로 플랜 혜택이 창작자 기능이라 기업 계정은 구독을 구매할 수 없다. */
    SUBSCRIPTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "기업 계정은 프로 플랜을 구독할 수 없습니다"),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이미 이용 중인 플랜입니다"),
    /** 월간↔연간 변경은 Customer Portal에서 처리한다. */
    SUBSCRIPTION_CHANGE_VIA_PORTAL(HttpStatus.CONFLICT, "플랜 변경은 결제 관리 페이지에서 진행해 주세요"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 이력이 없습니다"),
    PRICE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "상품이 아직 준비되지 않았습니다"),
    STRIPE_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "결제 요청을 처리하지 못했습니다"),
    INVALID_PRODUCT(HttpStatus.BAD_REQUEST, "요청한 상품 유형이 올바르지 않습니다");

    private final HttpStatus status;
    private final String message;

    BillingErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
