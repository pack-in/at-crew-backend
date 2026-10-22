package com.atcrew.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-only-jwt-secret-for-unit-tests-must-be-at-least-64-bytes-long!!", 3_600_000L, 2_592_000_000L);

    @Test
    void 같은_회원에게_연달아_발급한_refresh_토큰은_값이_다르다() {
        // iat가 초 단위라 jti가 없으면 같은 초에 발급한 두 토큰이 동일해 회전 1회용 보장이 깨진다
        String first = jwtProvider.generateRefreshToken("member-1");
        String second = jwtProvider.generateRefreshToken("member-1");

        assertThat(first).isNotEqualTo(second);
        assertThat(jwtProvider.validateRefreshToken(second)).isTrue();
        assertThat(jwtProvider.getMemberId(second)).isEqualTo("member-1");
    }
}
