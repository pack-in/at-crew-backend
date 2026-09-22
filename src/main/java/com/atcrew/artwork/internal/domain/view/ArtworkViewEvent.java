package com.atcrew.artwork.internal.domain.view;

import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * 유효 열람(FIRST·REVISIT) 원천 기록(홈-R14). 기간 조회수(핫 작품)는 이 기록만으로 계산한다.
 */
@Entity
@Table(name = "artwork_view_events")
public class ArtworkViewEvent implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "artwork_id")
    private String artworkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "viewer_type")
    private ArtworkViewerType viewerType;

    // 탈퇴 회원은 NULL로 비식별화된다 — 행은 남아 기간 조회수에 계속 포함된다.
    @Column(name = "viewer_key")
    private String viewerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "view_kind")
    private ArtworkViewKind viewKind;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    // 애플리케이션이 ID를 직접 할당하므로 Persistable로 신규 여부를 명시해 save()가 merge 대신 persist로 가게 한다.
    @Transient
    private boolean isNew = false;

    protected ArtworkViewEvent() {
    }

    public static ArtworkViewEvent record(String artworkId, ArtworkViewerType viewerType, String viewerKey,
                                          ArtworkViewKind viewKind, Instant viewedAt) {
        ArtworkViewEvent event = new ArtworkViewEvent();
        event.id = UuidV7Generator.generate();
        event.artworkId = artworkId;
        event.viewerType = viewerType;
        event.viewerKey = viewerKey;
        event.viewKind = viewKind;
        event.viewedAt = viewedAt;
        event.isNew = true;
        return event;
    }

    @Override
    public String getId() {
        return id;
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

    public String getArtworkId() { return artworkId; }
    public ArtworkViewerType getViewerType() { return viewerType; }
    public String getViewerKey() { return viewerKey; }
    public ArtworkViewKind getViewKind() { return viewKind; }
    public Instant getViewedAt() { return viewedAt; }
}
