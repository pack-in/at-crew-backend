package com.atcrew.billing;

/**
 * 기업 계정 여부를 알려주는 포트. 구현은 company 모듈이 제공한다.
 *
 * <p>billing이 company를 직접 참조하면 billing → company → recruit → billing 순환이 생기므로
 * 의존을 역전시켰다 — 인터페이스는 billing이 소유하고, 어댑터는 company에 둔다.
 */
public interface CompanyAccountPort {

    /** 기업 프로필 보유 여부. 프로 플랜 혜택이 창작자 기능이라 기업 계정은 구독 대상이 아니다. */
    boolean isCompanyAccount(String memberId);
}
