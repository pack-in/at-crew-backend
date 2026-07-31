-- MongoDB → MariaDB 전환 베이스라인 스키마.
-- 설계 근거: docs/design/mariadb-migration-design.md §3, §4
-- 이 시점(P1)에는 아직 어떤 모듈도 JPA 엔티티로 이 테이블을 참조하지 않는다 — P2~P4에서 모듈별로 순차 매핑한다.
-- id 컬럼은 애플리케이션이 생성하는 UUIDv7 문자열(§3.1). ASCII 전용이므로 latin1_bin으로 인덱스 키를 줄인다.
-- 모듈 경계를 넘는 FK는 걸지 않는다(§4 FK 정책) — bookmark_entries.artwork_id, banners.member_id 등.

-- ===================================================================
-- member 모듈
-- ===================================================================

CREATE TABLE members (
    id                      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    login_email             VARCHAR(255) NULL,
    auth_provider           VARCHAR(20)  NULL,
    handle                  VARCHAR(50)  NULL,
    name                    VARCHAR(50)  NULL,
    creator_role            VARCHAR(30)  NULL,
    password_hash           VARCHAR(255) NULL,
    email_verified          TINYINT(1)   NOT NULL DEFAULT 0,
    timezone                VARCHAR(50)  NULL,
    country_code            VARCHAR(2)   NULL,
    employment_status       VARCHAR(30)  NOT NULL DEFAULT 'PREPARING',
    experience_level        VARCHAR(30)  NULL,
    experience_rank         TINYINT      NOT NULL DEFAULT -1,
    total_slot_count        INT          NOT NULL DEFAULT 5,
    available_slot_count    INT          NOT NULL DEFAULT 5,
    contact                 VARCHAR(255) NULL,
    sns                     VARCHAR(255) NULL,
    tools                   VARCHAR(255) NULL,
    terms_privacy_policy    TINYINT(1)   NULL,
    terms_service_terms     TINYINT(1)   NULL,
    terms_third_party       TINYINT(1)   NULL,
    terms_marketing         TINYINT(1)   NULL,
    terms_agreed_at         DATETIME(6)  NULL,
    is_active               TINYINT(1)   NOT NULL DEFAULT 1,
    deleted_at               DATETIME(6)  NULL,
    last_login_at           DATETIME(6)  NULL,
    deleted_login_email     VARCHAR(255) NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_email_provider (login_email, auth_provider),
    UNIQUE KEY uk_members_handle (handle),
    KEY idx_members_deleted_email (deleted_login_email),
    KEY idx_members_search_latest (is_active, employment_status, updated_at DESC),
    KEY idx_members_search_experience (is_active, employment_status, experience_rank DESC, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_careers (
    id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id     VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    work_title    VARCHAR(255) NULL,
    role          VARCHAR(255) NULL,
    start_date    DATE NULL,
    end_date      DATE NULL,
    ongoing       TINYINT(1) NOT NULL DEFAULT 0,
    description   VARCHAR(200) NULL,
    PRIMARY KEY (id),
    KEY idx_mc_member (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_activity_fields (
    member_id       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    activity_field  VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, activity_field),
    KEY idx_maf_field (activity_field, member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_active_regions (
    member_id       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value           VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_team_experiences (
    member_id       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value           VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ===================================================================
-- auth 모듈
-- ===================================================================

CREATE TABLE refresh_tokens (
    id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id     VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    token_value   VARCHAR(500) NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_token (token_value),
    KEY idx_rt_member (member_id),
    KEY idx_rt_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE login_attempts (
    attempt_key       VARCHAR(350) NOT NULL,
    fail_count        INT NOT NULL DEFAULT 0,
    first_failed_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (attempt_key),
    KEY idx_la_first_failed (first_failed_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ===================================================================
-- artwork 모듈
-- ===================================================================

CREATE TABLE artworks (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    author_id                   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title                       VARCHAR(255) NULL,
    description                 TEXT NULL,
    representative_image_index  INT NOT NULL DEFAULT 0,
    thumbnail_key               VARCHAR(500) NULL,
    image_layout_type           VARCHAR(30) NULL,
    artwork_field               VARCHAR(30) NULL,
    creative_type               VARCHAR(30) NULL,
    work_duration                VARCHAR(30) NULL,
    cut_count                   INT NULL,
    video_links                 JSON NULL,
    age_rating                  VARCHAR(30) NULL,
    visibility                  VARCHAR(30) NULL,
    visibility_before_delete    VARCHAR(30) NULL,
    status                      VARCHAR(30) NOT NULL,
    deleted_at                  DATETIME(6) NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_aw_author (author_id),
    KEY idx_aw_retry (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_images (
    id                  BIGINT AUTO_INCREMENT NOT NULL,
    artwork_id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal             INT NOT NULL,
    original_key        VARCHAR(500) NULL,
    thumb_key           VARCHAR(500) NULL,
    thumb_adult_key     VARCHAR(500) NULL,
    original_avif_key   VARCHAR(500) NULL,
    processing_status   VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_order (artwork_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_materials (
    id                BIGINT AUTO_INCREMENT NOT NULL,
    artwork_id        VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal           INT NOT NULL,
    name              VARCHAR(255) NULL,
    targets           JSON NULL,
    attachment_keys   JSON NULL,
    links             JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_am_order (artwork_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_roles (
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(30) NOT NULL,
    PRIMARY KEY (artwork_id, value),
    KEY idx_ar_value (value, artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_genres (
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(50) NOT NULL,
    PRIMARY KEY (artwork_id, value),
    KEY idx_ag_value (value, artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_tags (
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(50) NOT NULL,
    PRIMARY KEY (artwork_id, value),
    KEY idx_at_value (value, artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE artwork_tools (
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(50) NOT NULL,
    PRIMARY KEY (artwork_id, value),
    KEY idx_ato_value (value, artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE orphaned_image_keys (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    keys_json   JSON NULL,
    marked_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_oik_marked (marked_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE bookmark_folders (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bf_member_name (member_id, name),
    KEY idx_bf_member_sort (member_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE bookmark_entries (
    id                            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id                     VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artwork_id                    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    folder_id                     VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NULL,
    artwork_visibility_at_save    VARCHAR(30) NULL,
    saved_at                      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_be_member_artwork (member_id, artwork_id),
    KEY idx_be_cursor (member_id, folder_id, saved_at DESC, id),
    KEY idx_be_folder (folder_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ===================================================================
-- community 모듈
-- ===================================================================

CREATE TABLE banners (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    image_url   VARCHAR(500) NULL,
    link_url    VARCHAR(500) NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    status      VARCHAR(30) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bn_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
