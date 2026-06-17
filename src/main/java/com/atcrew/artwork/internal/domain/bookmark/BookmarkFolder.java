package com.atcrew.artwork.internal.domain.bookmark;

import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "bookmarkFolders")
public class BookmarkFolder {

    @Id
    private String id;

    private String memberId;
    private String name;
    private int sortOrder;

    @CreatedDate
    private Instant createdAt;

    protected BookmarkFolder() {
    }

    public static BookmarkFolder create(String memberId, String name, int sortOrder) {
        BookmarkFolder folder = new BookmarkFolder();
        folder.memberId = memberId;
        folder.name = name.strip();
        folder.sortOrder = sortOrder;
        return folder;
    }

    public void assertOwner(String memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_ACCESS_DENIED);
        }
    }

    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
