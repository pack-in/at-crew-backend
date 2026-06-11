package com.atcrew.member;

public record RegisterMemberCommand(
        String loginEmail,
        String name,
        AuthProvider authProvider,
        AccountType accountType,
        String companyName,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
