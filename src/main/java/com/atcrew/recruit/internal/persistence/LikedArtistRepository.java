package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.CompanyArtistId;
import com.atcrew.recruit.internal.domain.LikedArtist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LikedArtistRepository extends JpaRepository<LikedArtist, CompanyArtistId> {

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
