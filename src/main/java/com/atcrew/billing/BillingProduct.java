package com.atcrew.billing;

/**
 * 판매 상품. 구독형 2종과 단건 게시 상품 3종으로 나뉜다(기획서 요금제-R02·R04~R06).
 *
 * <p>가격·통화(USD)·Stripe Price ID는 설정(`billing.products.*`)에 있고, 실제 청구액의 정본은 Stripe다.
 */
public enum BillingProduct {

    PRO_MONTHLY(Type.SUBSCRIPTION),
    PRO_YEARLY(Type.SUBSCRIPTION),

    /** 팀원 모집글 업로드 권한 — 구매 시 끌어올리기도 1회 함께 지급된다(요금제-R06). */
    TEAM_POSTING(Type.ONE_TIME),
    /** 끌어올리기 — 적용 시 48시간 상단 고정(recruit §2.1.1). */
    BOOST(Type.ONE_TIME),
    /** 구인글 업로드 권한 — 기업 전용(구인구직-R02). */
    JOB_POSTING(Type.ONE_TIME);

    public enum Type { SUBSCRIPTION, ONE_TIME }

    private final Type type;

    BillingProduct(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public boolean isSubscription() {
        return type == Type.SUBSCRIPTION;
    }

    public PlanType toPlanType() {
        return switch (this) {
            case PRO_MONTHLY -> PlanType.PRO_MONTHLY;
            case PRO_YEARLY -> PlanType.PRO_YEARLY;
            default -> throw new IllegalStateException("구독 상품이 아닙니다: " + this);
        };
    }
}
