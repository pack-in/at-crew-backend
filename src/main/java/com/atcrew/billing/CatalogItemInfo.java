package com.atcrew.billing;

/**
 * 요금제 페이지의 상품 카드 한 장(요금제-R03~R05).
 *
 * <p>금액은 USD 센트 단위 정수다($5.99 = 599). 표시 문구·혜택 카피는 프론트가 갖는다.
 *
 * @param product     상품 키
 * @param amount      청구 금액(센트)
 * @param listAmount  취소선으로 표기할 정가(센트). 할인이 없으면 null
 * @param currency    통화 — USD 단일
 * @param cta         버튼 상태
 */
public record CatalogItemInfo(
        BillingProduct product,
        long amount,
        Long listAmount,
        String currency,
        CtaState cta
) {

    /** 상품 카드의 버튼 상태. 실제 라벨("시작하기"/"이용 중인 플랜")은 프론트가 매핑한다. */
    public enum CtaState {
        /** 구매·시작 가능 */
        AVAILABLE,
        /** 현재 이용 중인 플랜 — 비활성 */
        CURRENT,
        /** 다른 주기의 프로 플랜으로 변경 */
        CHANGE,
        /** 구매 불가 — 기업 계정의 프로 플랜 */
        UNAVAILABLE
    }
}
