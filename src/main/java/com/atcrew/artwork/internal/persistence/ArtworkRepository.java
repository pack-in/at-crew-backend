package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
    @Query("SELECT a FROM Artwork a WHERE a.authorId = :authorId AND a.status NOT IN :excludedStatuses")
    List<Artwork> findByAuthorIdAndStatusNotInForUpdate(
            @Param("authorId") String authorId, @Param("excludedStatuses") Collection<ArtworkStatus> excludedStatuses);

    // 조회수 원자적 증가(이슈 #78) — 읽고-더하고-쓰는 방식은 동시 조회가 서로의 증가를 덮어쓴다.
    // 낙관적 락(@Version)을 쓰면 인기 작품일수록 충돌로 조회 자체가 실패하므로 벌크 UPDATE로 처리한다
    // (recruit JobPostingRepository.incrementViewCount와 동일 패턴).
    @Modifying
    @Query("UPDATE Artwork a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") String id);

    // 북마크 수 원자적 증가 — BookmarkService.saveBookmark 전용
    @Modifying
    @Query("UPDATE Artwork a SET a.bookmarkCount = a.bookmarkCount + 1 WHERE a.id = :id")
    void incrementBookmarkCount(@Param("id") String id);

    /**
     * 북마크 수 원자적 감소 — BookmarkService.removeBookmark 전용.
     *
     * <p>0에서 멈추는 CASE 가드를 둔다. 증감이 벌크 UPDATE라 저장 이력과 카운터가 어긋날 수 있고
     * (마이그레이션 유입분, 동시 삭제), 한 번이라도 음수가 되면 정렬이 영구히 뒤틀린다.
     * JPA 표준에 GREATEST가 없어 CASE 식으로 같은 동작을 표현한다.
     */
    @Modifying
    @Query("UPDATE Artwork a SET a.bookmarkCount = CASE WHEN a.bookmarkCount > 0 THEN a.bookmarkCount - 1 ELSE 0 END WHERE a.id = :id")
    void decrementBookmarkCount(@Param("id") String id);

    // getArtworksForReindex — 생성순 오름차순, 커서 있음/없음
    List<Artwork> findByCreatedAtAfterOrderByCreatedAtAsc(Instant cursor, Pageable pageable);

    List<Artwork> findAllByOrderByCreatedAtAsc(Pageable pageable);

    /** 휴지통 보관 기간이 지난 작품 id, 오래된 것부터 — {@code TrashPurgeScheduler}가 작품마다 따로 지운다(#178). */
    @Query("select a.id from Artwork a where a.status = :status and a.deletedAt < :threshold order by a.deletedAt asc")
    List<String> findIdsByStatusAndDeletedAtBefore(@Param("status") ArtworkStatus status,
                                                   @Param("threshold") Instant threshold, Pageable pageable);

    /** 후보 중 어떤 작품이 사용자 지정 썸네일로 쓰고 있는 key — 보존 판정(#216·#229)에 쓴다. */
    @Query("select a.thumbnailKey from Artwork a where a.thumbnailKey in :keys")
    List<String> findUsedThumbnailKeys(@Param("keys") Collection<String> keys);

    /**
     * 후보 중 어떤 작품이 자료 첨부로 쓰고 있는 key. 첨부는 JSON 배열 컬럼이라 JSON_TABLE로 펼쳐 비교한다
     * (V41 백필과 같은 방식).
     */
    @Query(value = """
            SELECT DISTINCT jt.k FROM artwork_materials am,
                JSON_TABLE(am.attachment_keys, '$[*]' COLUMNS (k VARCHAR(500) PATH '$')) jt
            WHERE jt.k IN (:keys)
            """, nativeQuery = true)
    List<String> findUsedAttachmentKeys(@Param("keys") Collection<String> keys);

}
