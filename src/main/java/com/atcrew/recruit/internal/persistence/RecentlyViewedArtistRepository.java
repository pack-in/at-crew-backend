package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.CompanyArtistId;
import com.atcrew.recruit.internal.domain.RecentlyViewedArtist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecentlyViewedArtistRepository extends JpaRepository<RecentlyViewedArtist, CompanyArtistId> {

    // 최근 본 작가 목록 첫 페이지 — (viewedAt, artistMemberId) 내림차순
    @Query("""
            SELECT v FROM RecentlyViewedArtist v
            WHERE v.id.companyMemberId = :companyMemberId
            ORDER BY v.viewedAt DESC, v.id.artistMemberId DESC
            """)
    List<RecentlyViewedArtist> findFirstPage(@Param("companyMemberId") String companyMemberId, Pageable pageable);

    // 최근 본 작가 목록 다음 페이지 — (viewedAt, artistMemberId) 복합 커서 keyset
    @Query("""
            SELECT v FROM RecentlyViewedArtist v
            WHERE v.id.companyMemberId = :companyMemberId
              AND (v.viewedAt < :cursorViewedAt
                   OR (v.viewedAt = :cursorViewedAt AND v.id.artistMemberId < :cursorArtistMemberId))
            ORDER BY v.viewedAt DESC, v.id.artistMemberId DESC
            """)
    List<RecentlyViewedArtist> findNextPage(@Param("companyMemberId") String companyMemberId,
            @Param("cursorViewedAt") Instant cursorViewedAt,
            @Param("cursorArtistMemberId") String cursorArtistMemberId, Pageable pageable);
}
