package com.atcrew.billing;

/**
 * Stripe Customer Portal URL. 구독 취소·결제수단 변경·영수증 조회는 전부 이 화면이 담당한다.
 */
public record PortalSessionInfo(String portalUrl) {
}
