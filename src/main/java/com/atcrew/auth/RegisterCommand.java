package com.atcrew.auth;

import com.atcrew.member.AccountType;

public record RegisterCommand(
        String firebaseIdToken,
        AccountType accountType,
        String companyName,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
