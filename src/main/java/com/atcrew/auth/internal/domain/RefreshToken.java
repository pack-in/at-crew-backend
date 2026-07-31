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

@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken implements Persistable<String> {

    @Id
    private String id;

    private String memberId;

    private String tokenValue;

    // MongoDB TTL 인덱스를 대체 — 만료 판별은 조회 쿼리 조건이, 행 정리는 AuthCleanupScheduler가 담당한다
    // (docs/design/mariadb-migration-design.md §3.5.2)
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // 신규 여부를 명시하지 않으면 save()가 매번 merge()(선행 SELECT)로 동작한다.
    // RefreshToken은 생성·삭제만 있어 @Version을 두지 않으므로(§3.4) Persistable로 처리한다.
    @Transient
    private boolean isNew = false;

    protected RefreshToken() {}

    private RefreshToken(String memberId, String tokenValue, Instant expiresAt) {
        this.id = UuidV7Generator.generate();
        this.memberId = memberId;
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
        this.isNew = true;
    }

    public static RefreshToken of(String memberId, String tokenValue, Instant expiresAt) {
        return new RefreshToken(memberId, tokenValue, expiresAt);
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getTokenValue() { return tokenValue; }
    public Instant getExpiresAt() { return expiresAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
