package com.atcrew.member;

public record RegisterMemberCommand(
        String loginEmail,
        String name,
        AuthProvider authProvider,
        String rawPassword,       // EMAIL일 때 필수 (해싱은 member.internal에서 수행), GOOGLE이면 null
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeThirdParty,  // [필수] 개인정보 제3자 제공 동의
        boolean agreeMarketing,
        String timezone,          // IANA tz ID, 가입 시 클라이언트 자동감지값
        String countryCode,       // ISO 3166-1 alpha-2, 거주 국가
        Language primaryLanguage  // 주 사용 언어, 가입 시 필수·이후 변경 불가(로그인-R19)
) {
    @Override
    public String toString() {
        return "RegisterMemberCommand[loginEmail=" + loginEmail +
               ", name=" + name + ", authProvider=" + authProvider +
               ", rawPassword=****, agreePrivacy=" + agreePrivacy +
               ", agreeService=" + agreeService + ", agreeThirdParty=" + agreeThirdParty +
               ", agreeMarketing=" + agreeMarketing + ", timezone=" + timezone +
               ", countryCode=" + countryCode +
               ", primaryLanguage=" + primaryLanguage + "]";
    }
}
