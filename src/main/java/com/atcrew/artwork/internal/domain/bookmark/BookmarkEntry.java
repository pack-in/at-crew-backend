package com.atcrew.artwork.internal.domain.bookmark;

import com.atcrew.artwork.Visibility;
import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "bookmark_entries")
public class BookmarkEntry implements Persistable<String> {

    @Id
    private String id;

    private String memberId;
    private String artworkId;
    private String folderId;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "artwork_visibility_at_save")
    private Visibility artworkVisibilityAtSave;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // Persistable로 신규 여부를 명시해 신규 엔티티는 persist(INSERT)로 바로 처리되게 한다.
    @Transient
    private boolean isNew = false;

    protected BookmarkEntry() {
    }

    public static BookmarkEntry create(String memberId, String artworkId, String folderId,
                                       Visibility artworkVisibilityAtSave) {
        BookmarkEntry entry = new BookmarkEntry();
        entry.id = UuidV7Generator.generate();
        entry.memberId = memberId;
        entry.artworkId = artworkId;
        entry.folderId = folderId;
        entry.savedAt = Instant.now();
        entry.artworkVisibilityAtSave = artworkVisibilityAtSave;
        entry.isNew = true;
        return entry;
    }

    public void moveToFolder(String folderId) {
        this.folderId = folderId;
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getArtworkId() { return artworkId; }
    public String getFolderId() { return folderId; }
    public Instant getSavedAt() { return savedAt; }
    public Visibility getArtworkVisibilityAtSave() { return artworkVisibilityAtSave; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
