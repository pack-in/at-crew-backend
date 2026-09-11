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

// 비밀번호 변경(설정 화면) 1단계 재인증 토큰 (이슈 #152). PasswordResetToken과 동일한 패턴이지만
// 로그인 상태에서 현재 비밀번호를 다시 확인한 결과를 짧게 들고 있는 용도라 별도 엔티티로 둔다.
// 원문은 응답에만 담고 여기엔 SHA-256 해시(tokenHash)만 저장한다.
@Entity
@Table(name = "password_reauth_tokens")
@EntityListeners(AuditingEntityListener.class)
public class PasswordReauthToken implements Persistable<String> {

    @Id
    private String id;

    private String memberId;

    private String tokenHash;

    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    protected PasswordReauthToken() {}

    private PasswordReauthToken(String memberId, String tokenHash, Instant expiresAt) {
        this.id = UuidV7Generator.generate();
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.isNew = true;
    }

    public static PasswordReauthToken of(String memberId, String tokenHash, Instant expiresAt) {
        return new PasswordReauthToken(memberId, tokenHash, expiresAt);
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
