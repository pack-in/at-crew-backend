package com.atcrew.auth.internal.infra.google;

import com.atcrew.member.AuthProvider;

public record GoogleUser(
        String email,           // Google 계정 이메일
        AuthProvider provider,  // 인증 제공자 — 이 경로에서는 항상 GOOGLE
        boolean emailVerified   // Google 측 이메일 인증 여부(email_verified claim)
) {}
