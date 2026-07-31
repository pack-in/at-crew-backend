package com.atcrew.artwork.internal.domain.artwork;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "artwork_materials")
public class Material {

    // 순수 내부 자식 행 — 외부에 노출되지 않으므로 대리키(auto-increment)로 충분하다(§4).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 양방향 매핑(Artwork.materials의 mappedBy) — 단방향 @JoinColumn은 artwork_id NOT NULL 제약과 충돌한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artwork_id", nullable = false)
    private Artwork artwork;

    private Integer ordinal;

    private String name;

    // 표시 전용 나열 데이터 — 검색·개별 수정이 없어 정규화 대신 JSON 컬럼으로 저장(§3.2)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> targets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_keys")
    private List<String> attachmentKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> links;

    protected Material() {
    }

    public Material(String name, List<String> targets, List<String> attachmentKeys, List<String> links) {
        this.name = name;
        this.targets = targets;
        this.attachmentKeys = attachmentKeys;
        this.links = links;
    }

    // Artwork 애그리게잇에 편입될 때 부모·순서를 부여한다 (같은 패키지에서만 호출)
    void attachTo(Artwork artwork, int ordinal) {
        this.artwork = artwork;
        this.ordinal = ordinal;
    }

    public String getName() { return name; }
    public List<String> getTargets() { return targets; }
    public List<String> getAttachmentKeys() { return attachmentKeys; }
    public List<String> getLinks() { return links; }
}
