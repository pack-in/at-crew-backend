package com.atcrew.auth;

public record EmailRegisterCommand(
        String email,
        String password,          // raw — 해싱은 member 모듈에서 수행
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing
) {
    @Override
    public String toString() {
        return "EmailRegisterCommand[email=" + email + ", password=****, name=" + name +
               ", agreeService=" + agreeService + ", agreePrivacy=" + agreePrivacy +
               ", agreeThirdParty=" + agreeThirdParty + ", agreeMarketing=" + agreeMarketing + "]";
    }
}
