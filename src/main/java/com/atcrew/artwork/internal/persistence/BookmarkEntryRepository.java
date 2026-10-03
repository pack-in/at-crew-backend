package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookmarkEntryRepository extends JpaRepository<BookmarkEntry, String> {

    boolean existsByMemberIdAndArtworkId(String memberId, String artworkId);

    Optional<BookmarkEntry> findByMemberIdAndArtworkId(String memberId, String artworkId);

    // 폴더 지정 조회 — 첫 페이지(커서 없음). saved_at만으로는 동률일 때 순서가 안 갈려 id를 2차 키로 둔다.
    List<BookmarkEntry> findByMemberIdAndFolderIdOrderBySavedAtDescIdDesc(
            String memberId, String folderId, Pageable pageable);

    // 폴더 지정 조회 — 커서 있음. saved_at은 DATETIME(6)이라 마이크로초까지 저장되는데 커서를 통째로
    // 비교하면 같은 시각의 뒷부분 행이 건너뛰어진다 — PortfolioServiceImpl.PortfolioCursor와 같은 이유로
    // (saved_at, id) 복합 비교를 쓴다.
    @Query("""
            SELECT e FROM BookmarkEntry e
            WHERE e.memberId = :memberId AND e.folderId = :folderId
              AND (e.savedAt < :savedAt OR (e.savedAt = :savedAt AND e.id < :id))
            ORDER BY e.savedAt DESC, e.id DESC
            """)
    List<BookmarkEntry> findByMemberIdAndFolderIdBeforeCursor(
            @Param("memberId") String memberId, @Param("folderId") String folderId,
            @Param("savedAt") Instant savedAt, @Param("id") String id, Pageable pageable);

    // 폴더 삭제 시 소속 북마크 전부를 기본 폴더로 이동시키기 위한 전체 조회 (페이지네이션 불필요)
    List<BookmarkEntry> findByMemberIdAndFolderIdOrderBySavedAtDesc(String memberId, String folderId);

    // 기본 폴더(folderId = null) 조회 — 첫 페이지
    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullOrderBySavedAtDescIdDesc(
            String memberId, Pageable pageable);

    // 기본 폴더(folderId = null) 조회 — 커서 있음 (위와 같은 이유로 (saved_at, id) 복합 비교)
    @Query("""
            SELECT e FROM BookmarkEntry e
            WHERE e.memberId = :memberId AND e.folderId IS NULL
              AND (e.savedAt < :savedAt OR (e.savedAt = :savedAt AND e.id < :id))
            ORDER BY e.savedAt DESC, e.id DESC
            """)
    List<BookmarkEntry> findByMemberIdAndFolderIdIsNullBeforeCursor(
            @Param("memberId") String memberId,
            @Param("savedAt") Instant savedAt, @Param("id") String id, Pageable pageable);

    List<BookmarkEntry> findByMemberIdAndArtworkIdIn(String memberId, List<String> artworkIds);

    void deleteByFolderId(String folderId);
}
