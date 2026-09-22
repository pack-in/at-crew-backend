package com.atcrew.artwork.internal.domain.view;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 작품 열람 24시간 dedup 판정 상태 — (작품, 열람자)당 한 행, 마지막으로 집계된 시각만 둔다
 * (member의 {@code ProfileView}와 같은 구조).
 *
 * <p>행 생성·갱신은 전부 {@code ArtworkViewDedupRepository}의 조건부 UPDATE와 INSERT IGNORE로만 한다.
 * 엔티티로 저장하지 않으므로 {@code Persistable} 신규 판별이 필요 없다.
 */
@Entity
@Table(name = "artwork_view_dedup")
@IdClass(ArtworkViewDedup.Key.class)
public class ArtworkViewDedup {

    @Id
    @Column(name = "artwork_id")
    private String artworkId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "viewer_type")
    private ArtworkViewerType viewerType;

    @Id
    @Column(name = "viewer_key")
    private String viewerKey;

    @Column(name = "last_counted_at")
    private Instant lastCountedAt;

    protected ArtworkViewDedup() {
    }

    public String getArtworkId() { return artworkId; }
    public ArtworkViewerType getViewerType() { return viewerType; }
    public String getViewerKey() { return viewerKey; }
    public Instant getLastCountedAt() { return lastCountedAt; }

    public static class Key implements Serializable {
        private String artworkId;
        private ArtworkViewerType viewerType;
        private String viewerKey;

        public Key() {
        }

        public Key(String artworkId, ArtworkViewerType viewerType, String viewerKey) {
            this.artworkId = artworkId;
            this.viewerType = viewerType;
            this.viewerKey = viewerKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(artworkId, key.artworkId)
                    && viewerType == key.viewerType
                    && Objects.equals(viewerKey, key.viewerKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artworkId, viewerType, viewerKey);
        }
    }
}
