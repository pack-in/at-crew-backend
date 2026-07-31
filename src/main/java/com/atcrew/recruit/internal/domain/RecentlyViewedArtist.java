package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기업 계정이 최근 조회한 작가 (설계 §2.7). 같은 작가를 다시 보면 {@code viewedAt}만 갱신한다.
 *
 * <p>기록은 동시 요청에서도 PK 충돌이 나지 않도록 리포지토리의 원자적 upsert로 수행하므로
 * 이 엔티티는 조회 매핑 전용이다.
 */
@Entity
@Table(name = "company_recently_viewed_artists")
public class RecentlyViewedArtist {

    @EmbeddedId
    private CompanyArtistId id;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    protected RecentlyViewedArtist() {
    }

    public CompanyArtistId getId() { return id; }
    public String getArtistMemberId() { return id.getArtistMemberId(); }
    public Instant getViewedAt() { return viewedAt; }
}
