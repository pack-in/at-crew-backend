package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    // 살아있는 윈도우의 실패 횟수만 조회 — 만료된 윈도우는 즉시 무시된다
    // (docs/design/mariadb-migration-design.md §3.5.2 — 정확성은 정리 배치가 아니라 쿼리 조건이 담당)
    @Query("SELECT a.failCount FROM LoginAttempt a WHERE a.attemptKey = :key AND a.firstFailedAt > :windowStart")
    Optional<Integer> findFailCountWithinWindow(String key, Instant windowStart);

    // Mongo upsert $inc 대체 — 단일 문장 원자 증가 (§3.3.4).
    // 윈도우가 만료된 행은 정리 배치가 지우기 전이라도 새 윈도우(failCount = 1)로 리셋한다 —
    // Mongo TTL이 문서를 지워 다음 실패가 새 윈도우를 시작했던 시맨틱을 그대로 유지하기 위함.
    // 영속성 컨텍스트에 LoginAttempt를 로드하는 경로가 없어 clear/flush 속성은 불필요하다.
    @Modifying
    @Query(value = """
            INSERT INTO login_attempts (attempt_key, fail_count, first_failed_at)
            VALUES (:key, 1, :now)
            ON DUPLICATE KEY UPDATE
                fail_count = IF(login_attempts.first_failed_at > :windowStart, login_attempts.fail_count + 1, 1),
                first_failed_at = IF(login_attempts.first_failed_at > :windowStart, login_attempts.first_failed_at, :now)
            """, nativeQuery = true)
    void increment(String key, Instant now, Instant windowStart);

    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.attemptKey = :key")
    void deleteByAttemptKey(String key);

    // TTL 인덱스 대체 정리 배치용 (§3.5.2)
    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.firstFailedAt < :threshold")
    int deleteExpired(Instant threshold);
}
