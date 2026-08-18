-- portfolio 모듈 스키마 (docs/design/portfolio-module-design.md §2)
--
-- ID 전략: 애플리케이션에서 UUIDv7(com.atcrew.common.id.UuidV7Generator)로 생성한 문자열.
-- ID 컬럼은 VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin(ASCII 전용, 인덱스 키 절감 + 대소문자 구분 비교 보장).
-- owner_member_id는 Member 참조, artwork_id/source_artwork_id는 Artwork 참조 — 모듈 경계상 FK를 걸지 않는다(V1 FK 정책과 동일).
--
-- 작가 페이지 포트폴리오는 가입 시 자동 생성하지 않는다 — 최초 조회·작품 추가 시 lazy 생성한다(§2.5).
-- 따라서 기존 회원 backfill 마이그레이션이 없다.

-- ============================================================
-- portfolios (작가 페이지 / 공유 포트폴리오)
-- ============================================================
CREATE TABLE portfolios (
    id                 VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    owner_member_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    kind               VARCHAR(20)  NOT NULL,                                        -- PortfolioKind (ARTIST_PAGE | SHARED)
    reflection_type    VARCHAR(20)  NOT NULL,                                        -- ReflectionType (LIVE | SNAPSHOT), ARTIST_PAGE는 항상 LIVE
    title              VARCHAR(100) NULL,                                            -- ARTIST_PAGE는 NULL — 화면은 사용자 이름 헤더를 쓴다
    share_slug         VARCHAR(32) CHARACTER SET latin1 COLLATE latin1_bin NULL,     -- SHARED만. SecureRandom 22자(§2.6)
    artist_page_key    VARCHAR(1)   NULL,                                            -- ARTIST_PAGE면 'Y', SHARED면 NULL — 회원당 1개 보장용 유니크 키
    item_count         INT          NOT NULL DEFAULT 0,                              -- 카드 "N개" 표기용 캐시
    snapshot_at             DATETIME(6)  NULL,                                       -- SNAPSHOT만
    snapshot_owner_name     VARCHAR(50)  NULL,                                       -- 고정형 프로필 스냅샷(마이페이지_작가-R44)
    snapshot_owner_profile  JSON         NULL,
    blocked_at         DATETIME(6)  NULL,                                            -- 탈퇴·운영 조치(POL-001). NULL이면 정상
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pf_slug (share_slug),
    -- NULL은 유니크 제약에서 중복으로 보지 않으므로 SHARED는 무제한, ARTIST_PAGE만 회원당 1개로 제한된다.
    UNIQUE KEY uk_pf_owner_artist_page (owner_member_id, artist_page_key),
    KEY idx_pf_owner_created (owner_member_id, created_at DESC, id),
    KEY idx_pf_owner_updated (owner_member_id, updated_at DESC, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- portfolio_items (LIVE·ARTIST_PAGE 전용 — 원본 작품 참조)
-- ============================================================
CREATE TABLE portfolio_items (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    portfolio_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artwork_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal      INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_order (portfolio_id, ordinal),
    UNIQUE KEY uk_pi_pf_artwork (portfolio_id, artwork_id),
    -- 작품 → 포함 포트폴리오 역조회(작품 삭제 시 정리, 라이브 멤버십 재계산)
    KEY idx_pi_artwork (artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- portfolio_item_snapshots (SNAPSHOT 전용 — 생성 시점 표시 필드 복사본)
-- ============================================================
-- 하이브리드 저장(§2.3) — 카드·커버 렌더에 쓰는 필드는 컬럼, 상세 본문은 payload_json 1컬럼.
CREATE TABLE portfolio_item_snapshots (
    id                BIGINT AUTO_INCREMENT NOT NULL,
    portfolio_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal           INT NOT NULL,
    source_artwork_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title             VARCHAR(255) NULL,
    thumb_key         VARCHAR(500) NULL,
    thumb_adult_key   VARCHAR(500) NULL,
    age_rating        VARCHAR(30)  NULL,                                             -- AgeRating
    artwork_field     VARCHAR(30)  NULL,                                             -- ArtworkField
    source_created_at DATETIME(6)  NULL,
    payload_json      JSON NULL,                                                     -- 상세: images/materials/tags/tools/roles/genres/videoLinks/description
    PRIMARY KEY (id),
    UNIQUE KEY uk_pis_order (portfolio_id, ordinal),
    KEY idx_pis_portfolio (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
