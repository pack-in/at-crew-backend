package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookmarkEntryRepository extends MongoRepository<BookmarkEntry, String> {

    boolean existsByMemberIdAndArtworkId(String memberId, String artworkId);

    Optional<BookmarkEntry> findByMemberIdAndArtworkId(String memberId, String artworkId);

    List<BookmarkEntry> findByMemberIdAndFolderIdAndSavedAtBeforeOrderBySavedAtDesc(
            String memberId, String folderId, Instant cursor);

    List<BookmarkEntry> findByMemberIdAndFolderIdOrderBySavedAtDesc(String memberId, String folderId);

    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullAndSavedAtBeforeOrderBySavedAtDesc(
            String memberId, Instant cursor);

    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullOrderBySavedAtDesc(String memberId);

    List<BookmarkEntry> findByMemberIdAndArtworkIdIn(String memberId, List<String> artworkIds);

    void deleteByFolderId(String folderId);
}
