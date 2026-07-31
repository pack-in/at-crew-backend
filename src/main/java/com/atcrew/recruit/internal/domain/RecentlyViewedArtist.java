package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기업 계정이 최근 조회한 작가 (설계 §2.7). 같은 작가를 다시 보면 {@code viewedAt}만 갱신한다(upsert).
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

    public static RecentlyViewedArtist create(String companyMemberId, String artistMemberId, Instant viewedAt) {
        RecentlyViewedArtist viewed = new RecentlyViewedArtist();
        viewed.id = new CompanyArtistId(companyMemberId, artistMemberId);
        viewed.viewedAt = viewedAt;
        return viewed;
    }

    public void renewViewedAt(Instant viewedAt) {
        this.viewedAt = viewedAt;
    }

    public CompanyArtistId getId() { return id; }
    public String getArtistMemberId() { return id.getArtistMemberId(); }
    public Instant getViewedAt() { return viewedAt; }
}
