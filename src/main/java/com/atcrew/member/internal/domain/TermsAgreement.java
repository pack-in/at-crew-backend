package com.atcrew.member.internal.domain;

import java.time.Instant;

public record TermsAgreement(
        boolean privacyPolicy,
        boolean serviceTerms,
        boolean marketingNotification,
        Instant agreedAt
) {
    public static TermsAgreement of(boolean privacyPolicy, boolean serviceTerms, boolean marketingNotification) {
        return new TermsAgreement(privacyPolicy, serviceTerms, marketingNotification, Instant.now());
    }
}
