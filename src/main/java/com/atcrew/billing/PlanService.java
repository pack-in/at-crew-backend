package com.atcrew.billing;

/**
 * 플랜 게이팅 조회용 공개 인터페이스 (docs/design/billing-module-design.md §1).
 *
 * <p>billing은 어떤 도메인 모듈도 참조하지 않는다 — 다른 모듈이 이 인터페이스로 단방향 조회만 한다.
 */
public interface PlanService {

    /** 회원의 현재 플랜을 조회한다. 구독 레코드가 없으면 스타터 기본값을 반환한다(null 없음). */
    PlanInfo getPlan(String memberId);

    /** 프로 권한 보유 여부 — plan이 프로이면서 status가 ACTIVE 또는 TRIALING일 때만 true다. */
    boolean isPro(String memberId);

    /** 프로 권한이 없으면 PRO_PLAN_REQUIRED(403) 예외를 던진다. */
    void assertPro(String memberId);

    /** 업로드 가능한 작품 수 상한 — 스타터 4개, 프로는 무제한. */
    int artworkLimit(String memberId);
}
