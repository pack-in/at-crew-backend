package com.atcrew.member;

public enum PasswordVerificationResult {
    MATCHED,
    /** 회원 부재(더미 BCrypt 수행됨) 또는 해시 불일치 — 호출자는 구분 불가 (timing-safe 보장) */
    MISMATCHED,
    /** 활성 EMAIL 회원이지만 passwordHash == null (마이그레이션 미완료) */
    PASSWORD_NOT_SET
}
