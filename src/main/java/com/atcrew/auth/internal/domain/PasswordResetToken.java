package com.atcrew.auth.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// 비밀번호 재설정 토큰 (docs/design/auth-email-custom-redesign.md §7.3). RefreshToken과 동일하게
// Mongo TTL 인덱스를 대체 — 만료 판별은 조회 쿼리 조건, 행 정리는 AuthCleanupScheduler가 담당한다.
// 원문 토큰은 이메일에만 담고 여기엔 SHA-256 해시(tokenHash)만 저장한다.
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken implements Persistable<String> {

    @Id
    private String id;

    private String memberId;

    private String tokenHash;

    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    protected PasswordResetToken() {}

    private PasswordResetToken(String memberId, String tokenHash, Instant expiresAt) {
        this.id = UuidV7Generator.generate();
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.isNew = true;
    }

    public static PasswordResetToken of(String memberId, String tokenHash, Instant expiresAt) {
        return new PasswordResetToken(memberId, tokenHash, expiresAt);
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
