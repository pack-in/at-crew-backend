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

    public boolean privacyPolicy() { return privacyPolicy; }
    public boolean serviceTerms() { return serviceTerms; }
    public boolean thirdPartyProvision() { return thirdPartyProvision; }
    public boolean marketingNotification() { return marketingNotification; }
    public Instant agreedAt() { return agreedAt; }
}
