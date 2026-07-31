package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookmarkEntryRepository extends JpaRepository<BookmarkEntry, String> {

    boolean existsByMemberIdAndArtworkId(String memberId, String artworkId);

    Optional<BookmarkEntry> findByMemberIdAndArtworkId(String memberId, String artworkId);

    // 폴더 지정 조회 — 커서(keyset) 있음/없음
    List<BookmarkEntry> findByMemberIdAndFolderIdAndSavedAtBeforeOrderBySavedAtDesc(
            String memberId, String folderId, Instant cursor, Pageable pageable);

    List<BookmarkEntry> findByMemberIdAndFolderIdOrderBySavedAtDesc(
            String memberId, String folderId, Pageable pageable);

    // 폴더 삭제 시 소속 북마크 전부를 기본 폴더로 이동시키기 위한 전체 조회 (페이지네이션 불필요)
    List<BookmarkEntry> findByMemberIdAndFolderIdOrderBySavedAtDesc(String memberId, String folderId);

    // 기본 폴더(folderId = null) 조회 — 커서 있음/없음
    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullAndSavedAtBeforeOrderBySavedAtDesc(
            String memberId, Instant cursor, Pageable pageable);

    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullOrderBySavedAtDesc(
            String memberId, Pageable pageable);

    List<BookmarkEntry> findByMemberIdAndArtworkIdIn(String memberId, List<String> artworkIds);

    void deleteByFolderId(String folderId);
}
