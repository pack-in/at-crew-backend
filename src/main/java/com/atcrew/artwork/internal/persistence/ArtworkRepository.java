package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArtworkRepository extends JpaRepository<Artwork, String>, JpaSpecificationExecutor<Artwork> {

    /**
     * 이미지 처리완료 이벤트가 같은 작품에 대해 동시에 여러 건 들어와도 READY 판정이 서로의 갱신을
     * 놓치지 않도록 부모 행에 비관적 락을 건다 — {@code ArtworkMediaEventListener} 전용
     * (동시성 레이스 수정, docs/NEXT_STEPS.md "지금 바로 처리할 것" 0번).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Artwork a WHERE a.id = :id")
    Optional<Artwork> findByIdForUpdate(@Param("id") String id);

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

    // 스타터 플랜 작품 개수 제한 — 휴지통(DELETED)은 제외한 보유 작품 수(마이페이지_작가-R20)
    long countByAuthorIdAndStatusNot(String authorId, ArtworkStatus status);

    /**
     * 스타터 제한 검사를 동시 요청 사이에 직렬화하기 위해 보유 작품 행에 비관적 락을 건다 —
     * 락 없이 개수만 세면 동시 업로드/복구 두 건이 같은 카운트를 보고 둘 다 통과해 제한을 넘길 수 있다
     * ({@code ArtworkServiceImpl.assertArtworkQuota} 전용).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Artwork a WHERE a.authorId = :authorId AND a.status <> :excludedStatus")
    List<Artwork> findByAuthorIdAndStatusNotForUpdate(
            @Param("authorId") String authorId, @Param("excludedStatus") ArtworkStatus excludedStatus);

    // getArtworksForReindex — 생성순 오름차순, 커서 있음/없음
    List<Artwork> findByCreatedAtAfterOrderByCreatedAtAsc(Instant cursor, Pageable pageable);

    List<Artwork> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
