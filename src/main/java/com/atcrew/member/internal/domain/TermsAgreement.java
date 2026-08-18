package com.atcrew.member.internal.domain;

import jakarta.persistence.Embeddable;

import java.time.Instant;

// MariaDB 전환(docs/design/mariadb-migration-design.md §3.2) — JPA는 record 임베더블을 지원하지
// 않아 record에서 일반 클래스로 전환. 접근자 이름은 기존 record 컴포넌트와 동일하게 유지한다.
@Embeddable
public class TermsAgreement {

    private boolean privacyPolicy;
    private boolean serviceTerms;
    private boolean thirdPartyProvision;   // [필수] 개인정보 제3자 제공 동의
    private boolean marketingNotification;
    private Instant agreedAt;

    protected TermsAgreement() {
    }

    private TermsAgreement(boolean privacyPolicy, boolean serviceTerms,
                           boolean thirdPartyProvision, boolean marketingNotification, Instant agreedAt) {
        this.privacyPolicy = privacyPolicy;
        this.serviceTerms = serviceTerms;
        this.thirdPartyProvision = thirdPartyProvision;
        this.marketingNotification = marketingNotification;
        this.agreedAt = agreedAt;
    }

    public static TermsAgreement of(boolean privacyPolicy, boolean serviceTerms,
                                    boolean thirdPartyProvision, boolean marketingNotification) {
        return new TermsAgreement(privacyPolicy, serviceTerms, thirdPartyProvision, marketingNotification, Instant.now());
    }

    /**
     * 마케팅 수신 동의만 바꾼 새 인스턴스를 반환한다(설정 화면 토글).
     * 필수 약관의 동의 시각({@code agreedAt})은 가입 시점 그대로 보존한다 — 마케팅 토글은
     * 필수 약관에 다시 동의한 것이 아니므로 감사 추적상 시각을 갱신하면 안 된다.
     */
    public TermsAgreement withMarketingNotification(boolean agreed) {
        return new TermsAgreement(privacyPolicy, serviceTerms, thirdPartyProvision, agreed, agreedAt);
    }

    public boolean privacyPolicy() { return privacyPolicy; }
    public boolean serviceTerms() { return serviceTerms; }
    public boolean thirdPartyProvision() { return thirdPartyProvision; }
    public boolean marketingNotification() { return marketingNotification; }
    public Instant agreedAt() { return agreedAt; }
}
