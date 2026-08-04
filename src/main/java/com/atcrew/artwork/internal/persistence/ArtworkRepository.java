package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface ArtworkRepository extends JpaRepository<Artwork, String>, JpaSpecificationExecutor<Artwork> {

    // 탈퇴 회원 작품 일괄 처리
    List<Artwork> findAllByAuthorId(String authorId);

    // getMyArtworks — 커서 있음/없음
    List<Artwork> findByAuthorIdAndStatusNotAndCreatedAtBeforeOrderByCreatedAtDesc(
            String authorId, ArtworkStatus status, Instant cursor, Pageable pageable);

    List<Artwork> findByAuthorIdAndStatusNotOrderByCreatedAtDesc(
            String authorId, ArtworkStatus status, Pageable pageable);

    // getTrashArtworks — 커서 있음/없음
    List<Artwork> findByAuthorIdAndStatusAndCreatedAtBeforeOrderByCreatedAtDesc(
            String authorId, ArtworkStatus status, Instant cursor, Pageable pageable);

    List<Artwork> findByAuthorIdAndStatusOrderByCreatedAtDesc(
            String authorId, ArtworkStatus status, Pageable pageable);

    // getArtworksForReindex — 생성순 오름차순, 커서 있음/없음
    List<Artwork> findByCreatedAtAfterOrderByCreatedAtAsc(Instant cursor, Pageable pageable);

    List<Artwork> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
