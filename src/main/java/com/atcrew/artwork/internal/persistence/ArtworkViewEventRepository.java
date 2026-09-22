package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.view.ArtworkViewEvent;
import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ArtworkViewEventRepository extends JpaRepository<ArtworkViewEvent, String> {

    /** 탈퇴 회원 비식별화 — 행은 남기고 열람자 식별값만 지운다(PA-09). 기간 조회수는 그대로 유지된다. */
    @Modifying
    @Query("""
            UPDATE ArtworkViewEvent e SET e.viewerKey = NULL
            WHERE e.viewerType = :viewerType AND e.viewerKey = :viewerKey
            """)
    int anonymizeViewer(@Param("viewerType") ArtworkViewerType viewerType, @Param("viewerKey") String viewerKey);

    /**
     * 기준 시각 이전 이벤트를 (작품, UTC 날짜, 열람자 유형, 열람 종류)별로 세어 일별 통계에 더한다(PA-06).
     *
     * <p>이미 같은 날짜 행이 있으면 더한다 — 보관 기준 시각이 하루 중간에 걸리면 같은 날짜가 이틀에 나눠
     * 합산되기 때문이다. 합산한 원본은 같은 트랜잭션에서 {@link #deleteViewedBefore}로 지워야 재실행 시
     * 이중 합산이 없다. viewed_at은 UTC로 저장되므로 DATE(viewed_at)이 곧 UTC 날짜다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO artwork_view_daily_stats (artwork_id, stat_date, viewer_type, view_kind, view_count)
            SELECT artwork_id, DATE(viewed_at), viewer_type, view_kind, COUNT(*)
            FROM artwork_view_events
            WHERE viewer_type = :viewerType AND viewed_at < :before
            GROUP BY artwork_id, DATE(viewed_at), viewer_type, view_kind
            ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count)
            """, nativeQuery = true)
    int accumulateDailyStatsBefore(@Param("viewerType") String viewerType, @Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM ArtworkViewEvent e WHERE e.viewerType = :viewerType AND e.viewedAt < :before")
    int deleteViewedBefore(@Param("viewerType") ArtworkViewerType viewerType, @Param("before") Instant before);
}
