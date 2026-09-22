package com.atcrew.artwork.internal.domain.view;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 핫 작품 순위용 최근 168시간 기간 조회수(홈-R03·R14). 매시 정각 배치가 통째로 다시 계산하며,
 * 애플리케이션은 읽기만 한다(핫 작품 조회의 정렬 키).
 */
@Entity
@Table(name = "artwork_hot_scores")
public class ArtworkHotScore {

    @Id
    @Column(name = "artwork_id")
    private String artworkId;

    @Column(name = "window_views")
    private long windowViews;

    @Column(name = "computed_at")
    private Instant computedAt;

    protected ArtworkHotScore() {
    }

    public String getArtworkId() { return artworkId; }
    public long getWindowViews() { return windowViews; }
    public Instant getComputedAt() { return computedAt; }
}
