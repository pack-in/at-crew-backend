package com.atcrew.member.internal.domain;

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

/**
 * handle 원장 행 (docs/design/member-handle-design.md §1.1).
 *
 * <p>"이 handle은 이 회원 소유"라는 영구 기록이라 삭제·수정하지 않는다. PK인 handle 컬럼은 utf8mb4_unicode_ci라
 * DB 조회·유일성은 대소문자 무관이다. 표시용 대소문자는 {@code members.handle}(현재값 사본)을 쓴다.
 */
@Entity
@Table(name = "member_handles")
@EntityListeners(AuditingEntityListener.class)
public class MemberHandle implements Persistable<String> {

    @Id
    private String handle;

    private String memberId;

    @CreatedDate
    private Instant createdAt;

    // handle을 애플리케이션이 직접 들고 있어 Spring Data가 신규 여부를 판별하지 못한다. Persistable로 명시하지
    // 않으면 save()가 merge()로 동작해 다른 회원 소유 행을 PK 충돌 없이 덮어쓴다 — 재할당 금지가 무력화된다.
    @Transient
    private boolean isNew = false;

    protected MemberHandle() {
    }

    public MemberHandle(String handle, String memberId) {
        this.handle = handle;
        this.memberId = memberId;
        this.isNew = true;
    }

    @Override
    public String getId() { return handle; }
    public String getHandle() { return handle; }
    public String getMemberId() { return memberId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
