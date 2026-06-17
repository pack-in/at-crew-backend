package com.atcrew.artwork.internal.domain.bookmark;

import com.atcrew.artwork.Visibility;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "bookmarkEntries")
public class BookmarkEntry {

    @Id
    private String id;

    private String memberId;
    private String artworkId;
    private String folderId;
    private Instant savedAt;
    private Visibility artworkVisibilityAtSave;

    protected BookmarkEntry() {
    }

    public static BookmarkEntry create(String memberId, String artworkId, String folderId,
                                       Visibility artworkVisibilityAtSave) {
        BookmarkEntry entry = new BookmarkEntry();
        entry.memberId = memberId;
        entry.artworkId = artworkId;
        entry.folderId = folderId;
        entry.savedAt = Instant.now();
        entry.artworkVisibilityAtSave = artworkVisibilityAtSave;
        return entry;
    }

    public void moveToFolder(String folderId) {
        this.folderId = folderId;
    }

    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getArtworkId() { return artworkId; }
    public String getFolderId() { return folderId; }
    public Instant getSavedAt() { return savedAt; }
    public Visibility getArtworkVisibilityAtSave() { return artworkVisibilityAtSave; }
}
