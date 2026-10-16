package com.atcrew.auth;

import com.atcrew.member.Language;

public record GoogleRegisterCommand(
        String googleIdToken,     // Google이 발급한 ID Token
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing,
        String timezone,           // IANA tz ID, 가입 시 클라이언트 자동감지값
        String countryCode,        // ISO 3166-1 alpha-2, 거주 국가
        Language primaryLanguage,  // 주 사용 언어, 가입 시 필수·이후 변경 불가(로그인-R19)
        String handle              // 사용자 아이디. FE 전환기 동안 선택 — null이면 서버가 생성
) {}
