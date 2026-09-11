package com.atcrew.auth.internal.web.dto;

// resetToken은 confirm 단계에서만 쓰는 세션 토큰이다 — 원문은 응답에만 담기고 DB에는 해시만 저장된다.
public record PasswordResetVerifyResponse(String resetToken) {}
