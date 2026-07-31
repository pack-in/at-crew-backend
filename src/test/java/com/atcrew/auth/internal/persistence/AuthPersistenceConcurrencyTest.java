package com.atcrew.auth.internal.persistence;

import com.atcrew.SharedContainersConfig;
import com.atcrew.TestMongoConfig;
import com.atcrew.auth.internal.domain.RefreshToken;
import com.atcrew.common.id.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MariaDB 전환(docs/design/mariadb-migration-design.md §3.3.2 / §3.3.4)에서
 * Mongo 원자 연산을 SQL로 치환한 두 지점의 동시성 시맨틱 검증 (§7 리스크 3).
 *
 * <p>스레드마다 독립 트랜잭션을 열어 실제 커밋을 발생시켜야 하므로 테스트 클래스에는 트랜잭션을 걸지 않고
 * {@link TransactionTemplate}으로 경계를 직접 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ImportTestcontainers(SharedContainersConfig.class)
@Import(TestMongoConfig.class)
class AuthPersistenceConcurrencyTest {

    // LoginAttemptLimiter.WINDOW_SECONDS와 동일한 차단 윈도우(10분)
    private static final int WINDOW_SECONDS = 600;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    LoginAttemptRepository loginAttemptRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 같은_refresh_토큰을_동시에_소비하면_하나만_성공한다() throws Exception {
        String tokenValue = "concurrent-" + UUID.randomUUID();
        refreshTokenRepository.save(RefreshToken.of(
                UuidV7Generator.generate(), tokenValue, Instant.now().plusSeconds(3600)));

        List<Integer> results = runConcurrently(2, () -> consume(tokenValue));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(refreshTokenRepository.findByTokenValueAndExpiresAtAfter(tokenValue, Instant.now())).isEmpty();
    }

    @Test
    void 동시_로그인_실패_기록이_유실_없이_누적된다() throws Exception {
        String key = "email:concurrent-" + UUID.randomUUID() + "@test.com";
        int threads = 8;

        runConcurrently(threads, () -> {
            Instant now = Instant.now();
            loginAttemptRepository.increment(key, now, now.minusSeconds(WINDOW_SECONDS));
            return 1;
        });

        assertThat(loginAttemptRepository.findFailCountWithinWindow(key, Instant.now().minusSeconds(WINDOW_SECONDS)))
                .contains(threads);
    }

    @Test
    void 만료된_윈도우는_다음_실패에서_새_윈도우로_리셋된다() {
        String key = "email:expired-" + UUID.randomUUID() + "@test.com";
        Instant expired = Instant.now().minusSeconds(WINDOW_SECONDS + 100);

        // 이미 만료된 윈도우에 2회 누적 (정리 배치가 아직 지우지 않은 상태를 재현)
        inTransaction(() -> {
            loginAttemptRepository.increment(key, expired, expired.minusSeconds(WINDOW_SECONDS));
            loginAttemptRepository.increment(key, expired, expired.minusSeconds(WINDOW_SECONDS));
            return null;
        });
        assertThat(loginAttemptRepository.findFailCountWithinWindow(key, Instant.now().minusSeconds(WINDOW_SECONDS)))
                .isEmpty();

        // 만료 후 첫 실패는 누적이 아니라 새 윈도우 시작이어야 한다 (Mongo TTL 삭제 후 재삽입과 동일 시맨틱)
        inTransaction(() -> {
            Instant now = Instant.now();
            loginAttemptRepository.increment(key, now, now.minusSeconds(WINDOW_SECONDS));
            return null;
        });

        assertThat(loginAttemptRepository.findFailCountWithinWindow(key, Instant.now().minusSeconds(WINDOW_SECONDS)))
                .contains(1);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────

    // 조회 후 조건부 DELETE — 영향 행 수 1을 가져간 요청만 토큰을 소비한다 (§3.3.2)
    private int consume(String tokenValue) {
        return refreshTokenRepository.findByTokenValueAndExpiresAtAfter(tokenValue, Instant.now())
                .map(token -> refreshTokenRepository.deleteByIdReturningCount(token.getId()))
                .orElse(0);
    }

    private List<Integer> runConcurrently(int threads, java.util.function.Supplier<Integer> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return inTransaction(action::get);
                }));
            }
            startSignal.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
