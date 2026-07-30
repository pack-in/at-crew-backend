package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    // 파생 쿼리(SELECT 후 em.remove)로 두면 DELETE가 flush까지 미뤄지는데, Hibernate는 flush 시 INSERT를
    // DELETE보다 먼저 실행한다 — 같은 초에 재로그인하면 refresh JWT(iat/exp가 초 단위)가 이전 토큰과
    // 완전히 동일해져 uk_rt_token 충돌이 난다. 즉시 실행되는 bulk DELETE로 Mongo의 "먼저 지우고 넣는" 순서를 보존한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.memberId = :memberId")
    void deleteAllByMemberId(String memberId);

    // 만료 조건을 쿼리에 명시해 TTL 인덱스 대체 정리 배치의 지연이 보안에 영향을 주지 않게 한다
    // (docs/design/mariadb-migration-design.md §3.5.2)
    Optional<RefreshToken> findByTokenValueAndExpiresAtAfter(String tokenValue, Instant now);

    // Mongo findAndRemove 대체 — 영향 행 수 1을 가져가는 요청만 토큰을 소비한다 (§3.3.2).
    // 동시 요청이 먼저 삭제했다면 0이 반환되어 재사용 시도로 판별된다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.id = :id")
    int deleteByIdReturningCount(String id);

    // TTL 인덱스 대체 정리 배치용 (§3.5.2)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(Instant now);
}
