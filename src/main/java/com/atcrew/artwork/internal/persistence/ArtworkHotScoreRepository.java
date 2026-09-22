package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.view.ArtworkHotScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ArtworkHotScoreRepository extends JpaRepository<ArtworkHotScore, String> {

    // TRUNCATE는 DDL이라 암묵적으로 커밋돼 뒤의 재적재와 한 트랜잭션으로 묶이지 않는다 — DELETE를 쓴다.
    @Modifying
    @Query(value = "DELETE FROM artwork_hot_scores", nativeQuery = true)
    int deleteAllScores();

    /** [windowStart, now) 구간의 유효 열람 이벤트를 작품별로 세어 적재한다. 이벤트가 없는 작품은 행이 생기지 않는다. */
    @Modifying
    @Query(value = """
            INSERT INTO artwork_hot_scores (artwork_id, window_views, computed_at)
            SELECT artwork_id, COUNT(*), :now
            FROM artwork_view_events
            WHERE viewed_at >= :windowStart AND viewed_at < :now
            GROUP BY artwork_id
            """, nativeQuery = true)
    int insertWindowScores(@Param("windowStart") Instant windowStart, @Param("now") Instant now);
}
