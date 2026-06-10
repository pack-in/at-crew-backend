package com.atcrew.common;

public final class LogMask {

    private LogMask() {}

    /**
     * 이메일 마스킹 — 로그에 PII 원문 노출 방지
     * "test@example.com" → "te**@ex*****.com"
     */
    public static String email(String email) {
        if (email == null) return "(null)";
        int at = email.indexOf('@');
        if (at < 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        return maskPart(local, 2) + "@" + maskDomain(domain);
    }

    private static String maskPart(String s, int visiblePrefix) {
        if (s.length() <= visiblePrefix) return s + "*";
        return s.substring(0, visiblePrefix) + "*".repeat(Math.min(s.length() - visiblePrefix, 4));
    }

    private static String maskDomain(String domain) {
        int dot = domain.lastIndexOf('.');
        if (dot < 0) return "***";
        String name = domain.substring(0, dot);
        String tld = domain.substring(dot);
        return maskPart(name, 2) + tld;
    }
}
