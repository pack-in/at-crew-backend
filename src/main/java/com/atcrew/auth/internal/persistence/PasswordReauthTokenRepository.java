package com.atcrew.auth.internal.persistence;

import com.atcrew.auth.internal.domain.PasswordReauthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordReauthTokenRepository extends JpaRepository<PasswordReauthToken, String> {

    // memberId까지 함께 걸어 다른 회원의 토큰 문자열을 우연히·부정하게 넣어도 매칭되지 않게 한다
    // (호출자는 이미 액세스 토큰으로 인증된 memberId를 알고 있으므로 추가 비용이 없다).
    Optional<PasswordReauthToken> findByMemberIdAndTokenHashAndExpiresAtAfter(
            String memberId, String tokenHash, Instant now);

    // Mongo findAndRemove 대체 — 영향 행 수 1을 가져가는 요청만 토큰을 소비한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordReauthToken t WHERE t.id = :id")
    int deleteByIdReturningCount(String id);

    // 재인증 재요청·로그아웃 시 이전 토큰을 무효화한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordReauthToken t WHERE t.memberId = :memberId")
    void deleteAllByMemberId(String memberId);

    // TTL 인덱스 대체 정리 배치용
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordReauthToken t WHERE t.expiresAt < :now")
    int deleteExpired(Instant now);
}
