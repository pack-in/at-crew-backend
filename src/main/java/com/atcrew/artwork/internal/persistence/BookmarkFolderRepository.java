package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository extends JpaRepository<BookmarkFolder, String> {

    List<BookmarkFolder> findByMemberIdOrderBySortOrderAsc(String memberId);

    boolean existsByMemberIdAndName(String memberId, String name);

    Optional<BookmarkFolder> findByIdAndMemberId(String id, String memberId);

    int countByMemberId(String memberId);
}
