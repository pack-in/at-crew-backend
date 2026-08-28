package com.atcrew.artwork.internal.domain.bookmark;

import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
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
@Table(name = "bookmark_folders")
@EntityListeners(AuditingEntityListener.class)
public class BookmarkFolder implements Persistable<String> {

    @Id
    private String id;

    private String memberId;
    private String name;

    @Column(name = "sort_order")
    private int sortOrder;

    @CreatedDate
    private Instant createdAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // Persistable로 신규 여부를 명시해 신규 엔티티는 persist(INSERT)로 바로 처리되게 한다.
    @Transient
    private boolean isNew = false;

    protected BookmarkFolder() {
    }

    public static BookmarkFolder create(String memberId, String name, int sortOrder) {
        BookmarkFolder folder = new BookmarkFolder();
        folder.id = UuidV7Generator.generate();
        folder.memberId = memberId;
        folder.name = name.strip();
        folder.sortOrder = sortOrder;
        folder.isNew = true;
        return folder;
    }

    public void assertOwner(String memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_ACCESS_DENIED);
        }
    }

    public void rename(String name) {
        this.name = name.strip();
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
