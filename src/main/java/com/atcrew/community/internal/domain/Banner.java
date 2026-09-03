package com.atcrew.community.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.community.BannerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "banners")
@EntityListeners(AuditingEntityListener.class)
public class Banner implements Persistable<String> {

    @Id
    private String id;

    private String memberId;
    private String imageUrl;
    private String linkUrl;
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    private BannerStatus status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
    // 변경 주체(이슈 #138). 회원 ID이거나 SYSTEM, 운영자 수동 UPDATE는 "ops:<담당자>".
    // 기록 시작 전 행은 NULL로 남는다.
    @LastModifiedBy
    @Column(name = "last_modified_by", length = 64)
    private String lastModifiedBy;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // @Version 없이는 save()가 신규/기존을 구분 못 해 매번 merge()(선행 SELECT)로 동작한다.
    // Persistable로 신규 여부를 명시해 신규 엔티티는 persist(INSERT)로 바로 처리되게 한다.
    @Transient
    private boolean isNew = false;

    protected Banner() {
    }

    public static Banner create(String memberId, String imageUrl, String linkUrl, int sortOrder) {
        Banner banner = new Banner();
        banner.id = UuidV7Generator.generate();
        banner.memberId = memberId;
        banner.imageUrl = imageUrl;
        banner.linkUrl = linkUrl;
        banner.sortOrder = sortOrder;
        banner.status = BannerStatus.ACTIVE;
        banner.isNew = true;
        return banner;
    }

    public void update(String imageUrl, String linkUrl, Integer sortOrder) {
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (linkUrl != null) this.linkUrl = linkUrl;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    public void delete() {
        this.status = BannerStatus.DELETED;
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public int getSortOrder() { return sortOrder; }
    public BannerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
