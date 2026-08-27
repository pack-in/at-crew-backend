package com.atcrew.auth;

import com.atcrew.member.Language;

public record GoogleRegisterCommand(
        String firebaseIdToken,
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing,
        String timezone,           // IANA tz ID, 가입 시 클라이언트 자동감지값
        String countryCode,        // ISO 3166-1 alpha-2, 거주 국가
        Language primaryLanguage   // 주 사용 언어, 가입 시 필수·이후 변경 불가(로그인-R19)
) {}
