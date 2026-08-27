package com.atcrew.auth;

import com.atcrew.member.Language;

public record EmailRegisterCommand(
        String email,
        String password,          // raw — 해싱은 member 모듈에서 수행
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing,
        String timezone,           // IANA tz ID, 가입 시 클라이언트 자동감지값
        String countryCode,        // ISO 3166-1 alpha-2, 거주 국가
        Language primaryLanguage   // 주 사용 언어, 가입 시 필수·이후 변경 불가(로그인-R19)
) {
    @Override
    public String toString() {
        return "EmailRegisterCommand[email=" + email + ", password=****, name=" + name +
               ", agreeService=" + agreeService + ", agreePrivacy=" + agreePrivacy +
               ", agreeThirdParty=" + agreeThirdParty + ", agreeMarketing=" + agreeMarketing +
               ", timezone=" + timezone + ", countryCode=" + countryCode +
               ", primaryLanguage=" + primaryLanguage + "]";
    }
}
