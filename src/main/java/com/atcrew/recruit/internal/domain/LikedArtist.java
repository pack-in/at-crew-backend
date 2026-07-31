package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기업 계정이 저장(♡)한 작가 (설계 §2.7). 해제는 행 삭제로 처리한다.
 */
@Entity
@Table(name = "company_liked_artists")
public class LikedArtist {

    @EmbeddedId
    private CompanyArtistId id;

    @Column(name = "liked_at", nullable = false)
    private Instant likedAt;

    protected LikedArtist() {
    }

    public static LikedArtist create(String companyMemberId, String artistMemberId, Instant likedAt) {
        LikedArtist likedArtist = new LikedArtist();
        likedArtist.id = new CompanyArtistId(companyMemberId, artistMemberId);
        likedArtist.likedAt = likedAt;
        return likedArtist;
    }

    public CompanyArtistId getId() { return id; }
    public String getArtistMemberId() { return id.getArtistMemberId(); }
    public Instant getLikedAt() { return likedAt; }
}
