package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.CompanyArtistId;
import com.atcrew.recruit.internal.domain.RecentlyViewedArtist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecentlyViewedArtistRepository extends JpaRepository<RecentlyViewedArtist, CompanyArtistId> {

    /**
     * 조회 기록 upsert — 같은 작가를 다시 보면 조회 시각만 갱신한다(설계 §2.7).
     * 조회 후 저장(check-then-act)은 동시 요청에서 PK 충돌을 일으키므로 원자적 upsert로 처리한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO company_recently_viewed_artists (company_member_id, artist_member_id, viewed_at)
            VALUES (:companyMemberId, :artistMemberId, :viewedAt)
            ON DUPLICATE KEY UPDATE viewed_at = :viewedAt
            """, nativeQuery = true)
    void upsert(@Param("companyMemberId") String companyMemberId, @Param("artistMemberId") String artistMemberId,
            @Param("viewedAt") Instant viewedAt);

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
