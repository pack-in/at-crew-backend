package com.atcrew.portfolio.internal.domain;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 고정형(SNAPSHOT) 포트폴리오의 작품 복사본 (docs/design/portfolio-module-design.md §2.3).
 *
 * <p>하이브리드 저장 — 카드·커버 렌더에 쓰는 필드는 컬럼으로, 상세 본문(images/materials/tags/tools/
 * roles/genres/videoLinks/description)은 {@code payloadJson} 1컬럼으로 둔다. 목록 조회가 JSON 파싱 없이
 * 끝나고 상세는 1회 파싱으로 해결된다.
 *
 * <p>이미지 R2 키는 복사하지 않고 원본 키를 그대로 참조한다(§5.6). 원본을 영구 삭제하거나 이미지를
 * 교체해도 이 행이 참조하는 키는 남아야 하므로, media의 삭제·고아 정리 경로가
 * {@code SnapshotRetainedMediaKeyProvider}로 보존 여부를 먼저 판정한다.
 */
@Entity
@Table(name = "portfolio_item_snapshots")
public class PortfolioItemSnapshot {

    // PK는 외부에 노출하지 않으므로 대리키로 충분하다(portfolio_items와 동일).
    // 외부 URL 식별자는 아래 snapshotPublicId를 쓴다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "portfolio_id", length = 36, nullable = false)
    private String portfolioId;

    // 스냅샷 상세(GET /api/portfolios/shared/{identifier}/snapshots/{snapshotId})의 외부 식별자.
    // PK를 노출하면 연속 번호로 타인 스냅샷을 열거할 수 있어 UUIDv7 문자열을 따로 발급한다(마이페이지_작가-R39).
    @Column(name = "snapshot_public_id", length = 36, nullable = false)
    private String snapshotPublicId;

    // 생성 시점 순서 고정(마이페이지_작가-R38) — 원본 createdAt 오름차순으로 채운다.
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    // 복제(§5.3) 시 후보 작품을 역산하기 위한 원본 식별자. 렌더에는 쓰지 않는다.
    @Column(name = "source_artwork_id", length = 36, nullable = false)
    private String sourceArtworkId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "thumb_key", length = 500)
    private String thumbKey;

    @Column(name = "thumb_adult_key", length = 500)
    private String thumbAdultKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", length = 30)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "artwork_field", length = 30)
    private ArtworkField artworkField;

    @Column(name = "source_created_at")
    private Instant sourceCreatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private String payloadJson;

    // 운영 정책·법적 조치에 따른 외부 노출 중단 시각(마이페이지_작가-R39). 원본 역조회가 아니라 스냅샷 행에
    // 비정규화해 둔다 — 원본을 영구 삭제하면 역조회로는 차단 판정 근거가 사라지기 때문이다.
    // 차단은 고정형 "상태 고정"보다 우선하며, 차단된 스냅샷은 카드·커버·개수·상세에서 모두 빠진다.
    @Column(name = "blocked_at")
    private Instant blockedAt;

    protected PortfolioItemSnapshot() {
    }

    public static PortfolioItemSnapshot of(String portfolioId, int ordinal, String sourceArtworkId,
                                           String title, String thumbKey, String thumbAdultKey,
                                           AgeRating ageRating, ArtworkField artworkField,
                                           Instant sourceCreatedAt, String payloadJson) {
        PortfolioItemSnapshot snapshot = new PortfolioItemSnapshot();
        snapshot.portfolioId = portfolioId;
        snapshot.snapshotPublicId = UuidV7Generator.generate();
        snapshot.ordinal = ordinal;
        snapshot.sourceArtworkId = sourceArtworkId;
        snapshot.title = title;
        snapshot.thumbKey = thumbKey;
        snapshot.thumbAdultKey = thumbAdultKey;
        snapshot.ageRating = ageRating;
        snapshot.artworkField = artworkField;
        snapshot.sourceCreatedAt = sourceCreatedAt;
        snapshot.payloadJson = payloadJson;
        return snapshot;
    }

    public Long getId() { return id; }
    public String getPortfolioId() { return portfolioId; }
    public String getSnapshotPublicId() { return snapshotPublicId; }
    public int getOrdinal() { return ordinal; }
    public String getSourceArtworkId() { return sourceArtworkId; }
    public String getTitle() { return title; }
    public String getThumbKey() { return thumbKey; }
    public String getThumbAdultKey() { return thumbAdultKey; }
    public AgeRating getAgeRating() { return ageRating; }
    public ArtworkField getArtworkField() { return artworkField; }
    public Instant getSourceCreatedAt() { return sourceCreatedAt; }
    public String getPayloadJson() { return payloadJson; }
    public boolean isBlocked() { return blockedAt != null; }
}
