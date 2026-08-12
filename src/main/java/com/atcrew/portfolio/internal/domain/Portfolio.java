package com.atcrew.portfolio.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.portfolio.PortfolioKind;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.exception.PortfolioErrorCode;
import com.atcrew.portfolio.internal.exception.PortfolioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 포트폴리오 애그리게잇 (docs/design/portfolio-module-design.md §2.1).
 *
 * <p>작가 페이지(ARTIST_PAGE)는 회원당 1개이며 가입 시 자동 생성하지 않고 최초 조회·작품 추가 시
 * lazy 생성한다(§2.5). 공유(SHARED)는 프로 전용이며 개수 제한이 없다.
 *
 * <p>search/community의 {@code PostType.PORTFOLIO}는 Artwork를 가리키는 레거시 UI 용어이며
 * 이 도메인과 무관하다(§1.4).
 */
@Entity
@Table(name = "portfolios")
@EntityListeners(AuditingEntityListener.class)
public class Portfolio implements Persistable<String> {

    // ARTIST_PAGE의 유니크 키 값 — SHARED는 null이라 (owner_member_id, artist_page_key) 유니크 제약이
    // 회원당 ARTIST_PAGE 1개만 허용하고 SHARED는 무제한이 된다(§2.5).
    private static final String ARTIST_PAGE_KEY = "Y";

    @Id
    private String id;

    private String ownerMemberId;

    @Enumerated(EnumType.STRING)
    private PortfolioKind kind;

    @Enumerated(EnumType.STRING)
    private ReflectionType reflectionType;

    // ARTIST_PAGE는 null — 화면은 사용자 이름 헤더를 쓴다(§2.1).
    private String title;

    // SHARED만. SecureRandom 22자 slug(§2.6) — 발급은 서비스 책임이라 생성자로 주입받는다.
    private String shareSlug;

    private String artistPageKey;

    // 카드 "N개" 표기용 캐시(열람 가능 작품 수 기준).
    private int itemCount;

    private Instant snapshotAt;

    private String snapshotOwnerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_owner_profile")
    private String snapshotOwnerProfileJson;

    // 탈퇴·운영 조치(POL-001)로 열람이 막힌 시점. null이면 정상(§5.2).
    private Instant blockedAt;

    // 작품 추가/제거와 관리자 조치가 동시에 들어올 수 있는 애그리게잇이라 낙관적 락을 둔다
    // (docs/design/mariadb-migration-design.md §3.4).
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // "업데이트순" 정렬 기준 (마이페이지_작가-R37) — [수정하기](updatePortfolio)로 저장한 시점만 기록한다.
    // updatedAt은 작품 추가/제거·정합성 재계산 같은 시스템 변경에도 갱신돼 정렬 기준으로 쓸 수 없다.
    private Instant lastEditedAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // 신규 여부를 명시하지 않으면 save()가 매번 merge()(선행 SELECT)로 동작한다.
    @Transient
    private boolean isNew = false;

    protected Portfolio() {
    }

    /** 작가 페이지 포트폴리오 — 항상 LIVE이고 제목·공유 슬러그가 없다. */
    public static Portfolio createArtistPage(String ownerMemberId) {
        Portfolio portfolio = newPortfolio(ownerMemberId, PortfolioKind.ARTIST_PAGE, ReflectionType.LIVE);
        portfolio.artistPageKey = ARTIST_PAGE_KEY;
        return portfolio;
    }

    /** 공유 포트폴리오 — 반영 유형(LIVE/SNAPSHOT)은 생성 시 결정되고 이후 전환할 수 없다(§2). */
    public static Portfolio createShared(String ownerMemberId, ReflectionType reflectionType,
                                         String title, String shareSlug) {
        Portfolio portfolio = newPortfolio(ownerMemberId, PortfolioKind.SHARED, reflectionType);
        portfolio.title = title;
        portfolio.shareSlug = shareSlug;
        return portfolio;
    }

    private static Portfolio newPortfolio(String ownerMemberId, PortfolioKind kind, ReflectionType reflectionType) {
        Portfolio portfolio = new Portfolio();
        portfolio.id = UuidV7Generator.generate();
        portfolio.ownerMemberId = ownerMemberId;
        portfolio.kind = kind;
        portfolio.reflectionType = reflectionType;
        portfolio.itemCount = 0;
        // 생성도 사용자의 편집 행위이므로 정렬 기준을 생성 시점으로 시작한다(감사 컬럼이 아니라 NOT NULL).
        portfolio.lastEditedAt = Instant.now();
        portfolio.isNew = true;
        return portfolio;
    }

    /** 작가 페이지 포트폴리오는 제목 자체를 갖지 않으므로 변경도 허용하지 않는다(§2.1, §4 PATCH). */
    public void updateTitle(String title) {
        if (kind == PortfolioKind.ARTIST_PAGE) {
            throw new PortfolioException(PortfolioErrorCode.ARTIST_PAGE_TITLE_IMMUTABLE);
        }
        this.title = title;
    }

    public void updateItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    /**
     * [수정하기]로 저장한 시점을 기록한다 — "업데이트순" 정렬 기준(마이페이지_작가-R37).
     * 작품 추가/제거 API나 원본 변경에 따른 재계산은 이 값을 건드리지 않는다.
     */
    public void markEdited() {
        this.lastEditedAt = Instant.now();
    }

    /** 탈퇴·운영 조치로 공유 열람을 막는다(§5.2). 이미 차단된 포트폴리오는 최초 차단 시점을 유지한다. */
    public void block() {
        if (blockedAt == null) {
            this.blockedAt = Instant.now();
        }
    }

    /** 고정형 생성 시 작성자 프로필까지 함께 얼린다(마이페이지_작가-R44). */
    public void markAsSnapshot(String ownerName, String ownerProfileJson) {
        this.snapshotAt = Instant.now();
        this.snapshotOwnerName = ownerName;
        this.snapshotOwnerProfileJson = ownerProfileJson;
    }

    @Override
    public String getId() { return id; }
    public String getOwnerMemberId() { return ownerMemberId; }
    public PortfolioKind getKind() { return kind; }
    public ReflectionType getReflectionType() { return reflectionType; }
    public String getTitle() { return title; }
    public String getShareSlug() { return shareSlug; }
    public String getArtistPageKey() { return artistPageKey; }
    public int getItemCount() { return itemCount; }
    public Instant getSnapshotAt() { return snapshotAt; }
    public String getSnapshotOwnerName() { return snapshotOwnerName; }
    public String getSnapshotOwnerProfileJson() { return snapshotOwnerProfileJson; }
    public Instant getBlockedAt() { return blockedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
