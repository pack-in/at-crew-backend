package com.atcrew.artwork.internal.persistence;

import com.atcrew.artwork.internal.domain.view.ArtworkViewDedup;
import com.atcrew.artwork.internal.domain.view.ArtworkViewerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ArtworkViewDedupRepository extends JpaRepository<ArtworkViewDedup, ArtworkViewDedup.Key> {

    /**
     * 직전 집계가 기준 시각보다 오래됐을 때만 집계 시각을 갱신한다. 영향 행 수가 1이면 24시간이 지난
     * 재방문(REVISIT)이고, 0이면 행이 없거나(최초 열람 후보) 아직 24시간이 지나지 않은 반복 열람이다.
     */
    @Modifying
    @Query("""
            UPDATE ArtworkViewDedup d SET d.lastCountedAt = :now
            WHERE d.artworkId = :artworkId
              AND d.viewerType = :viewerType
              AND d.viewerKey = :viewerKey
              AND d.lastCountedAt < :staleBefore
            """)
    int touchIfStale(@Param("artworkId") String artworkId,
                     @Param("viewerType") ArtworkViewerType viewerType,
                     @Param("viewerKey") String viewerKey,
                     @Param("now") Instant now,
                     @Param("staleBefore") Instant staleBefore);

    /**
     * 행이 없을 때만 넣는다. 1이면 최초 열람(FIRST), 0이면 그 사이 다른 요청이 먼저 넣은 것이다.
     *
     * <p>PK 충돌을 예외가 아니라 영향 행 수 0으로 받으려고 INSERT IGNORE를 쓴다. 예외로 받으면
     * (member {@code ProfileViewCounter}의 saveAndFlush 방식) 리포지토리 트랜잭션 경계를 넘는 순간 바깥
     * 트랜잭션이 rollback-only가 되고, Hibernate 세션도 flush 실패 후 상태를 보장하지 않는다.
     * IGNORE가 PK 충돌 외의 오류(NOT NULL 위반 등)까지 경고로 낮추는 부작용은 값이 전부 애플리케이션이
     * 검증한 식별자라 여기서는 생기지 않는다.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO artwork_view_dedup (artwork_id, viewer_type, viewer_key, last_counted_at)
            VALUES (:artworkId, :viewerType, :viewerKey, :now)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("artworkId") String artworkId,
                       @Param("viewerType") String viewerType,
                       @Param("viewerKey") String viewerKey,
                       @Param("now") Instant now);

    /** 탈퇴 회원 비식별화 — 해당 열람자의 dedup 행을 지운다(PA-09). */
    @Modifying
    @Query("DELETE FROM ArtworkViewDedup d WHERE d.viewerType = :viewerType AND d.viewerKey = :viewerKey")
    int deleteByViewer(@Param("viewerType") ArtworkViewerType viewerType, @Param("viewerKey") String viewerKey);

    /** 보관 기간이 지난 dedup 행을 지운다 — 익명 기록 1년 보관 배치 전용(PA-06). */
    @Modifying
    @Query("DELETE FROM ArtworkViewDedup d WHERE d.viewerType = :viewerType AND d.lastCountedAt < :before")
    int deleteCountedBefore(@Param("viewerType") ArtworkViewerType viewerType, @Param("before") Instant before);
}
