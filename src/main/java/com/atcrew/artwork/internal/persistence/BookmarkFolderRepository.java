package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository extends JpaRepository<BookmarkFolder, String> {

    List<BookmarkFolder> findByMemberIdOrderBySortOrderAsc(String memberId);

    boolean existsByMemberIdAndName(String memberId, String name);

    // 이름 변경 시 자기 자신은 중복 검사에서 제외한다 — 같은 이름으로 재저장해도 충돌이 나면 안 된다.
    boolean existsByMemberIdAndNameAndIdNot(String memberId, String name, String id);

    Optional<BookmarkFolder> findByIdAndMemberId(String id, String memberId);

    int countByMemberId(String memberId);
}
