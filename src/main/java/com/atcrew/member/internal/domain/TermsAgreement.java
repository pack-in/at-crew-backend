package com.atcrew.member.internal.domain;

import java.time.Instant;

public record TermsAgreement(
        boolean privacyPolicy,
        boolean serviceTerms,
        boolean thirdPartyProvision,   // [필수] 개인정보 제3자 제공 동의
        boolean marketingNotification,
        Instant agreedAt
) {
    public static TermsAgreement of(boolean privacyPolicy, boolean serviceTerms,
                                    boolean thirdPartyProvision, boolean marketingNotification) {
        return new TermsAgreement(privacyPolicy, serviceTerms, thirdPartyProvision, marketingNotification, Instant.now());
    }
}
