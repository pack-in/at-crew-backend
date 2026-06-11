package com.atcrew.auth;

public record RegisterCommand(
        String firebaseIdToken,
        String name,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
