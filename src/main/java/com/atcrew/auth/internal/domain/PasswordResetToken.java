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

// 비밀번호 재설정 토큰 (docs/design/auth-email-custom-redesign.md §7.3, 이슈 #151로 OTP 코드 방식 전환).
// RefreshToken과 동일하게 Mongo TTL 인덱스를 대체 — 만료 판별은 조회 쿼리 조건, 행 정리는
// AuthCleanupScheduler가 담당한다. 원문(코드 또는 세션 토큰)은 이메일·응답에만 담고 여기엔 해시만 저장한다.
//
// 상태 전이는 같은 행을 재사용한다 — 신규 테이블을 만들지 않는다.
//   PENDING  (verifiedAt == null): tokenHash = HMAC-SHA256(6자리 코드, pepper). expiresAt = 코드 TTL.
//   VERIFIED (verifiedAt != null): 코드 검증 성공 시 tokenHash를 SHA-256(재설정 세션 토큰)으로 덮어쓰고
//                                  expiresAt을 세션 TTL로 갱신한다 — 컬럼을 새로 추가하지 않는다.
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken implements Persistable<String> {

    @Id
    private String id;

    private String memberId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant verifiedAt;

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

    // 코드 검증 성공 — 같은 행을 세션 상태로 전이시킨다(§ 클래스 주석). PENDING 행이 아니면(이미
    // VERIFIED) 호출하지 않는 것이 호출자 책임 — AuthServiceImpl.verifyPasswordResetCode에서 분기한다.
    public void markVerified(String sessionTokenHash, Instant sessionExpiresAt) {
        this.tokenHash = sessionTokenHash;
        this.expiresAt = sessionExpiresAt;
        this.verifiedAt = Instant.now();
    }

    public boolean isVerified() { return verifiedAt != null; }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
