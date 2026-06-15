package com.atcrew.member;

/**
 * verifyPassword 결과 — 검증 결과(enum)와 일치 시 memberId를 함께 반환해 중복 DB 조회를 제거한다.
 * auth 모듈은 MATCHED일 때 memberId를 바로 사용하므로 별도의 findByLoginEmailAndProvider 호출이 불필요하다.
 */
public record PasswordVerification(PasswordVerificationResult result, String memberId) {

    public static PasswordVerification matched(String memberId) {
        return new PasswordVerification(PasswordVerificationResult.MATCHED, memberId);
    }

    public static PasswordVerification mismatched() {
        return new PasswordVerification(PasswordVerificationResult.MISMATCHED, null);
    }

    public static PasswordVerification notSet() {
        return new PasswordVerification(PasswordVerificationResult.PASSWORD_NOT_SET, null);
    }

    public boolean isMatched() { return result == PasswordVerificationResult.MATCHED; }
    public boolean isMismatched() { return result == PasswordVerificationResult.MISMATCHED; }
    public boolean isNotSet() { return result == PasswordVerificationResult.PASSWORD_NOT_SET; }
}
