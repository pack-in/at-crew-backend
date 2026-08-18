package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    // 만료 조건을 쿼리에 명시해 TTL 인덱스 대체 정리 배치의 지연이 보안에 영향을 주지 않게 한다
    // (RefreshTokenRepository.findByTokenValueAndExpiresAtAfter와 동일 패턴).
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    // Mongo findAndRemove 대체 — 영향 행 수 1을 가져가는 요청만 토큰을 소비한다. 동시 confirm 요청이
    // 먼저 삭제했다면 0이 반환되어 재사용 시도로 판별된다(RefreshTokenRepository.deleteByIdReturningCount와 동일).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.id = :id")
    int deleteByIdReturningCount(String id);

    // 재요청 시 이전에 발급된 미사용 토큰을 무효화한다 — 오래된 이메일의 링크가 계속 유효한 채로
    // 남아있지 않게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.memberId = :memberId")
    void deleteAllByMemberId(String memberId);

    // TTL 인덱스 대체 정리 배치용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    int deleteExpired(Instant now);
}
