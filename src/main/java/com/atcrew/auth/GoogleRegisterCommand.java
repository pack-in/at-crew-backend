package com.atcrew.auth;

public record GoogleRegisterCommand(
        String firebaseIdToken,
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing,
        String timezone            // IANA tz ID, 가입 시 클라이언트 자동감지값
) {}
