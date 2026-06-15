package com.atcrew.auth;

public record GoogleRegisterCommand(
        String firebaseIdToken,
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing
) {}
