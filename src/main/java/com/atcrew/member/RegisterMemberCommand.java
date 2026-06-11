package com.atcrew.member;

public record RegisterMemberCommand(
        String loginEmail,
        String name,
        AuthProvider authProvider,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
