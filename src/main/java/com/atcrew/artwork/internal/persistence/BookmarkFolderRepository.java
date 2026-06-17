package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository extends MongoRepository<BookmarkFolder, String> {

    List<BookmarkFolder> findByMemberIdOrderBySortOrderAsc(String memberId);

    boolean existsByMemberIdAndName(String memberId, String name);

    Optional<BookmarkFolder> findByIdAndMemberId(String id, String memberId);

    int countByMemberId(String memberId);
}
