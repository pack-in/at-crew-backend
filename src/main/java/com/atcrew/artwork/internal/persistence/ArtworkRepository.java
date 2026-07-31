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

    // Worker 재시도 스케줄러 — 지정 시각 이전부터 PROCESSING 상태인 작품
    List<Artwork> findByStatusAndUpdatedAtBefore(ArtworkStatus status, Instant threshold);

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
