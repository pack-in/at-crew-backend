package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.persistence.LoginAttemptRepository;
import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * MongoDB TTL 인덱스 대체 정리 배치 (docs/design/mariadb-migration-design.md §3.5.2).
 *
 * <p>만료 판별의 정확성은 조회 쿼리 조건이 담당하고, 이 배치는 용량 관리만 담당한다 —
 * 배치가 늦게 돌아도 만료된 refresh token이 소비되거나 만료된 차단 윈도우가 유지되지 않는다.
 * fixedDelay라 시간대와 무관하며, 다중 인스턴스에서 중복 실행되어도 DELETE는 멱등이다.
 */
@Component
class AuthCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    AuthCleanupScheduler(RefreshTokenRepository refreshTokenRepository,
                         LoginAttemptRepository loginAttemptRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    void cleanupExpired() {
        Instant now = Instant.now();
        int expiredTokens = refreshTokenRepository.deleteExpired(now);
        int expiredAttempts = loginAttemptRepository
                .deleteExpired(now.minusSeconds(LoginAttemptLimiter.WINDOW_SECONDS));

        if (expiredTokens > 0 || expiredAttempts > 0) {
            log.info("만료 데이터 정리: refreshTokens={} loginAttempts={}", expiredTokens, expiredAttempts);
        }
    }
}
