package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.CompanyArtistId;
import com.atcrew.recruit.internal.domain.LikedArtist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LikedArtistRepository extends JpaRepository<LikedArtist, CompanyArtistId> {

    /**
     * 좋아요 저장 — 이미 저장돼 있으면 최초 저장 시각을 유지한다(멱등).
     * 조회 후 저장(check-then-act)은 동시 요청에서 PK 충돌을 일으키므로 원자적 upsert로 처리한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO company_liked_artists (company_member_id, artist_member_id, liked_at)
            VALUES (:companyMemberId, :artistMemberId, :likedAt)
            ON DUPLICATE KEY UPDATE liked_at = liked_at
            """, nativeQuery = true)
    void upsert(@Param("companyMemberId") String companyMemberId, @Param("artistMemberId") String artistMemberId,
            @Param("likedAt") Instant likedAt);

    // 좋아요한 작가 목록 첫 페이지 — (likedAt, artistMemberId) 내림차순
    @Query("""
            SELECT l FROM LikedArtist l
            WHERE l.id.companyMemberId = :companyMemberId
            ORDER BY l.likedAt DESC, l.id.artistMemberId DESC
            """)
    List<LikedArtist> findFirstPage(@Param("companyMemberId") String companyMemberId, Pageable pageable);

    // 좋아요한 작가 목록 다음 페이지 — (likedAt, artistMemberId) 복합 커서 keyset
    @Query("""
            SELECT l FROM LikedArtist l
            WHERE l.id.companyMemberId = :companyMemberId
              AND (l.likedAt < :cursorLikedAt
                   OR (l.likedAt = :cursorLikedAt AND l.id.artistMemberId < :cursorArtistMemberId))
            ORDER BY l.likedAt DESC, l.id.artistMemberId DESC
            """)
    List<LikedArtist> findNextPage(@Param("companyMemberId") String companyMemberId,
            @Param("cursorLikedAt") Instant cursorLikedAt,
            @Param("cursorArtistMemberId") String cursorArtistMemberId, Pageable pageable);
}
