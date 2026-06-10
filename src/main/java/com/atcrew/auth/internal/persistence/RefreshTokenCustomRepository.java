package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.RefreshToken;

import java.util.Optional;

interface RefreshTokenCustomRepository {

    // 단일 원자 연산 — 동시 요청 시 하나만 토큰을 가져감
    Optional<RefreshToken> findAndDeleteByTokenValue(String tokenValue);
}
