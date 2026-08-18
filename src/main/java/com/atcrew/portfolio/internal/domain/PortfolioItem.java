package com.atcrew.portfolio.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 라이브 포트폴리오(작가 페이지 + 최신 반영형)의 작품 구성 행 (docs/design/portfolio-module-design.md §2.2).
 *
 * <p>원본 작품을 ID로만 참조한다 — 표시 필드는 조회 시점에 artwork에서 읽으므로 원본 수정이 그대로 반영된다.
 * 고정형(SNAPSHOT)은 이 테이블을 쓰지 않고 {@link PortfolioItemSnapshot}에 복사본을 남긴다.
 */
@Entity
@Table(name = "portfolio_items")
public class PortfolioItem {

    // 순수 내부 자식 행 — 외부에 노출되지 않으므로 대리키로 충분하다(artwork_images와 동일).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "portfolio_id", length = 36, nullable = false)
    private String portfolioId;

    @Column(name = "artwork_id", length = 36, nullable = false)
    private String artworkId;

    // 업로드순(오래된순) 고정이 MVP 규칙이지만, 고정형과 순서 규칙을 통일하고 추후 순서 조정 기능을
    // 받기 위해 별도 컬럼으로 둔다(§2.2).
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    protected PortfolioItem() {
    }

    public static PortfolioItem of(String portfolioId, String artworkId, int ordinal) {
        PortfolioItem item = new PortfolioItem();
        item.portfolioId = portfolioId;
        item.artworkId = artworkId;
        item.ordinal = ordinal;
        return item;
    }

    public Long getId() { return id; }
    public String getPortfolioId() { return portfolioId; }
    public String getArtworkId() { return artworkId; }
    public int getOrdinal() { return ordinal; }
}
