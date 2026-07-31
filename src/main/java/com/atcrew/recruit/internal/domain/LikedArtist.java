package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 기업 계정이 저장(♡)한 작가 (설계 §2.7). 해제는 행 삭제로 처리한다.
 *
 * <p>저장은 동시 요청에서도 PK 충돌이 나지 않도록 리포지토리의 원자적 upsert로 수행하므로
 * 이 엔티티는 조회 매핑 전용이다.
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

    public CompanyArtistId getId() { return id; }
    public String getArtistMemberId() { return id.getArtistMemberId(); }
    public Instant getLikedAt() { return likedAt; }
}
