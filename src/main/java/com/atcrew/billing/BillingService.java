package com.atcrew.billing;

import java.util.List;

/**
 * 결제/구독 모듈의 공개 포트. 다른 모듈은 이 인터페이스로만 billing에 접근한다.
 *
 * <p>플랜과 단건 상품 보유 개수의 소유권은 billing에 있다 — Member에 plan 필드를 두지 않는다.
 */
public interface BillingService {

    /** 프로 플랜 혜택 사용 가능 여부. 결제 실패(PAST_DUE)·취소는 false다(설정-R03). */
    boolean hasProPlan(String memberId);

    /** 단건 상품 보유 개수. 구독 상품을 넘기면 예외다. */
    int getBalance(String memberId, BillingProduct product);

    /**
     * 단건 상품 1개를 차감한다. 게시가 성공한 뒤에만 호출한다(요금제-R06).
     *
     * <p>잔량이 없으면 403(ENTITLEMENT_REQUIRED) 도메인 예외를 던진다.
     *
     * @param refId 차감 대상 식별자(게시글 ID 등) — 원장 추적용
     */
    void consume(String memberId, BillingProduct product, String refId);

    /** 설정 &gt; 요금제 및 결제 탭용 현재 상태 — 플랜·구독 상태·다음 결제일·단건 상품 보유 개수. */
    BillingSummaryInfo getSummary(String memberId);

    /**
     * 요금제 카탈로그. 상품별 가격과 CTA 상태를 함께 계산한다(요금제-R03~R05).
     *
     * @param memberId 비로그인이면 null — 이때 CTA는 전부 구매 가능 상태로 내려간다
     */
    List<CatalogItemInfo> getCatalog(String memberId);

    /** Stripe Checkout 세션을 만들고 결제 페이지 URL을 반환한다. */
    CheckoutSessionInfo createCheckoutSession(String memberId, BillingProduct product);

    /** Stripe Customer Portal 세션을 만들고 구독 관리 페이지 URL을 반환한다. */
    PortalSessionInfo createPortalSession(String memberId);
}
