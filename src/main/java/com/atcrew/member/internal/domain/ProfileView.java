package com.atcrew.member.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 프로필 열람 기록 — 동일 조회자의 24시간 이내 반복 조회를 걸러내기 위한 최소 상태다
 * (기획서 마이페이지_작가-R03 "동일 사용자의 24시간 이내 반복 조회는 최초 1회만 집계").
 *
 * <p>조회자별 이력을 쌓지 않고 (작가, 조회자)당 한 행만 두고 마지막 조회 시각을 덮어쓴다 —
 * 필요한 것은 "직전 조회가 24시간 안이었는가" 하나뿐이라 이력 누적은 낭비다.
 */
@Entity
@Table(name = "member_profile_views")
@IdClass(ProfileView.Key.class)
public class ProfileView implements Persistable<ProfileView.Key> {

    @Id
    @Column(name = "artist_member_id")
    private String artistMemberId;

    @Id
    @Column(name = "viewer_member_id")
    private String viewerMemberId;

    private Instant viewedAt;

    // ID를 애플리케이션이 직접 들고 있어(복합 키) Spring Data가 신규 여부를 판별하지 못한다.
    // Persistable로 명시하지 않으면 save()가 persist() 대신 merge()로 동작해 PK 충돌 없이 덮어써
    // 24시간 중복 제외가 통째로 무력화된다(Member·Company와 동일한 이유로 같은 패턴을 쓴다).
    @Transient
    private boolean isNew = false;

    protected ProfileView() {
    }

    public ProfileView(String artistMemberId, String viewerMemberId, Instant viewedAt) {
        this.artistMemberId = artistMemberId;
        this.viewerMemberId = viewerMemberId;
        this.viewedAt = viewedAt;
        this.isNew = true;
    }

    @Override
    public Key getId() {
        return new Key(artistMemberId, viewerMemberId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getArtistMemberId() { return artistMemberId; }
    public String getViewerMemberId() { return viewerMemberId; }
    public Instant getViewedAt() { return viewedAt; }

    public static class Key implements Serializable {
        private String artistMemberId;
        private String viewerMemberId;

        public Key() {
        }

        public Key(String artistMemberId, String viewerMemberId) {
            this.artistMemberId = artistMemberId;
            this.viewerMemberId = viewerMemberId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(artistMemberId, key.artistMemberId)
                    && Objects.equals(viewerMemberId, key.viewerMemberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artistMemberId, viewerMemberId);
        }
    }
}
