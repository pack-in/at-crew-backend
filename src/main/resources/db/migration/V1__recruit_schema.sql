-- recruit 모듈 스키마 (docs/design/recruit-module-design.md §2, §8)
-- 이 저장소의 첫 Flyway 마이그레이션 — 기존 4개 모듈(artwork/community/member/auth)은 아직 MongoDB이므로
-- 이 시점의 베이스라인은 recruit 테이블만 포함한다.
--
-- ID 전략: 애플리케이션에서 UUIDv7(com.atcrew.common.id.UuidV7Generator)로 생성한 문자열.
-- ID 컬럼은 VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin(ASCII 전용, 인덱스 키 절감 + 대소문자 구분 비교 보장).
-- author_member_id/applicant_member_id는 Member(MongoDB) 참조 — 모듈 경계상 FK를 걸지 않는다.
--
-- MariaDB는 partial index(WHERE 절)를 지원하지 않으므로 §8의 partial index는 일반 복합 인덱스로 대체한다.

-- ============================================================
-- job_postings (구인글)
-- ============================================================
CREATE TABLE job_postings (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    author_member_id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title                       VARCHAR(200)   NOT NULL,
    company_name                VARCHAR(200)   NULL,
    ceo_name                    VARCHAR(100)   NULL,
    industry                    VARCHAR(100)   NULL,
    address                     VARCHAR(300)   NULL,
    contact                     VARCHAR(100)   NULL,
    website_url                 VARCHAR(500)   NULL,
    company_description         TEXT           NULL,
    is_business_registered      BOOLEAN        NOT NULL DEFAULT FALSE,
    is_resume_required          BOOLEAN        NOT NULL DEFAULT FALSE,
    is_cover_letter_required    BOOLEAN        NOT NULL DEFAULT FALSE,
    work_scope                  VARCHAR(500)   NULL,
    deadline                    DATE           NULL,   -- NULL이면 상시모집
    recruit_count               INT            NULL,
    hiring_process              TEXT           NULL,
    education                   VARCHAR(200)   NULL,
    experience                  VARCHAR(200)   NULL,
    age                         VARCHAR(100)   NULL,
    gender                      VARCHAR(50)    NULL,
    employment_type             VARCHAR(30)    NULL,   -- JobEmploymentType
    work_location_type          VARCHAR(30)    NULL,   -- JobWorkLocationType
    work_schedule_type          VARCHAR(30)    NULL,   -- JobWorkScheduleType
    core_time_start              TIME           NULL,   -- work_schedule_type=FLEXIBLE일 때만 사용
    core_time_end                TIME           NULL,
    has_overtime_pay            BOOLEAN        NOT NULL DEFAULT FALSE,
    has_social_insurance        BOOLEAN        NOT NULL DEFAULT FALSE,
    has_contract                BOOLEAN        NOT NULL DEFAULT FALSE,
    payment_type                VARCHAR(30)    NULL,   -- JobPaymentType
    payment_unit                VARCHAR(30)    NULL,   -- JobPaymentUnit
    min_amount                  BIGINT         NULL,
    max_amount                  BIGINT         NULL,
    is_negotiable                BOOLEAN        NOT NULL DEFAULT FALSE,
    mg_amount                   BIGINT         NULL,
    rs_ratio                    DECIMAL(5,2)   NULL,
    has_buyout                  BOOLEAN        NOT NULL DEFAULT FALSE,
    benefit_description          TEXT           NULL,
    benefit_keywords             JSON           NULL,   -- 표시 전용 리스트
    thumbnail_image              VARCHAR(500)   NULL,
    reference_images             JSON           NULL,   -- 표시 전용 리스트
    bookmark_count               BIGINT         NOT NULL DEFAULT 0,
    view_count                   BIGINT         NOT NULL DEFAULT 0,
    status                       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',  -- JobPostingStatus
    deleted_at                   DATETIME       NULL,
    version                      BIGINT         NOT NULL DEFAULT 0,       -- 낙관적 락
    created_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_postings_feed ON job_postings (status, created_at DESC);        -- 공개 목록(커서)
CREATE INDEX idx_job_postings_author ON job_postings (author_member_id, status);     -- 내 목록
CREATE INDEX idx_job_postings_admin_pending ON job_postings (status, created_at);    -- 관리자 PENDING 목록(partial index 미지원 대체)

-- 자식 테이블은 JPA @ElementCollection 기본 매핑(복합 PK, 서로게이트 id 없음)을 그대로 따른다.
-- (job_posting_id, role/genre) 복합 PK가 job_posting_id 선두 인덱스 역할도 겸하므로 별도 단일 컬럼 인덱스는 두지 않는다.
CREATE TABLE job_posting_roles (
    job_posting_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    role              VARCHAR(100) NOT NULL,
    PRIMARY KEY (job_posting_id, role),
    CONSTRAINT fk_job_posting_roles_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_posting_roles_role ON job_posting_roles (role);

CREATE TABLE job_posting_genres (
    job_posting_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    genre             VARCHAR(100) NOT NULL,
    PRIMARY KEY (job_posting_id, genre),
    CONSTRAINT fk_job_posting_genres_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_posting_genres_genre ON job_posting_genres (genre);

-- ============================================================
-- team_postings (팀원모집글)
-- ============================================================
CREATE TABLE team_postings (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    author_member_id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title                       VARCHAR(200)   NOT NULL,
    is_business_registered      BOOLEAN        NOT NULL DEFAULT FALSE,
    is_resume_required          BOOLEAN        NOT NULL DEFAULT FALSE,
    is_cover_letter_required    BOOLEAN        NOT NULL DEFAULT FALSE,
    author_name                 VARCHAR(100)   NULL,
    contact                     VARCHAR(100)   NULL,
    author_description           TEXT           NULL,
    recruit_purposes             JSON           NULL,   -- 표시 전용 리스트
    work_location_type          VARCHAR(30)    NULL,   -- TeamWorkLocationType
    activity_region              VARCHAR(200)   NULL,   -- work_location_type=ONLINE이면 NULL 강제(도메인 불변식)
    has_participation_fee       BOOLEAN        NOT NULL DEFAULT FALSE,
    has_profit_sharing          BOOLEAN        NOT NULL DEFAULT FALSE,
    extra_cost                  VARCHAR(500)   NULL,
    deadline                    DATE           NULL,
    recruit_count               INT            NULL,
    selection_process            TEXT           NULL,
    activity_duration            VARCHAR(30)    NULL,   -- TeamActivityDuration
    weekly_activity_time         VARCHAR(30)    NULL,   -- TeamWeeklyActivityTime
    project_description          TEXT           NULL,
    thumbnail_image              VARCHAR(500)   NULL,
    reference_images             JSON           NULL,   -- 표시 전용 리스트
    bookmark_count               BIGINT         NOT NULL DEFAULT 0,
    view_count                   BIGINT         NOT NULL DEFAULT 0,
    status                       VARCHAR(20)    NOT NULL DEFAULT 'PUBLISHED',  -- TeamPostingStatus (승인 절차 없음)
    deleted_at                   DATETIME       NULL,
    version                      BIGINT         NOT NULL DEFAULT 0,           -- 낙관적 락
    created_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_team_postings_feed ON team_postings (status, created_at DESC);       -- 공개 목록(커서)
CREATE INDEX idx_team_postings_author ON team_postings (author_member_id, status);    -- 내 목록

-- 자식 테이블은 job_posting_roles/genres와 동일하게 JPA @ElementCollection 기본 매핑(복합 PK, 서로게이트 id 없음)을 따른다.
CREATE TABLE team_posting_roles (
    team_posting_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    role              VARCHAR(100) NOT NULL,
    PRIMARY KEY (team_posting_id, role),
    CONSTRAINT fk_team_posting_roles_posting FOREIGN KEY (team_posting_id) REFERENCES team_postings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_team_posting_roles_role ON team_posting_roles (role);

CREATE TABLE team_posting_genres (
    team_posting_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    genre             VARCHAR(100) NOT NULL,
    PRIMARY KEY (team_posting_id, genre),
    CONSTRAINT fk_team_posting_genres_posting FOREIGN KEY (team_posting_id) REFERENCES team_postings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_team_posting_genres_genre ON team_posting_genres (genre);

-- ============================================================
-- job_seeking_posts (구직글, laiteu 대응 없음 — 신규 엔티티)
-- ============================================================
CREATE TABLE job_seeking_posts (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    author_member_id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title                       VARCHAR(200)   NOT NULL,
    drawing_style                VARCHAR(200)   NULL,
    preferred_feedback_style     VARCHAR(30)    NULL,   -- FeedbackStyle
    work_style                   VARCHAR(30)    NULL,   -- WorkStyle
    desired_rate                 VARCHAR(200)   NULL,
    portfolio_description         TEXT           NULL,
    reference_images              JSON           NULL,   -- 표시 전용 리스트
    status                       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',  -- JobSeekingPostStatus
    deleted_at                   DATETIME       NULL,
    version                      BIGINT         NOT NULL DEFAULT 0,       -- 낙관적 락
    created_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_seeking_posts_feed ON job_seeking_posts (status, created_at DESC);      -- 공개 목록(커서)
CREATE INDEX idx_job_seeking_posts_author ON job_seeking_posts (author_member_id, status);   -- 내 목록

CREATE TABLE job_seeking_post_roles (
    id                    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    job_seeking_post_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    role                  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_seeking_post_roles_post FOREIGN KEY (job_seeking_post_id) REFERENCES job_seeking_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_seeking_post_roles_post ON job_seeking_post_roles (job_seeking_post_id);
CREATE INDEX idx_job_seeking_post_roles_role ON job_seeking_post_roles (role);

CREATE TABLE job_seeking_post_genres (
    id                    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    job_seeking_post_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    genre                 VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_seeking_post_genres_post FOREIGN KEY (job_seeking_post_id) REFERENCES job_seeking_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_seeking_post_genres_post ON job_seeking_post_genres (job_seeking_post_id);
CREATE INDEX idx_job_seeking_post_genres_genre ON job_seeking_post_genres (genre);

-- ============================================================
-- job_applications (구인글 지원) — laiteu의 artworkPublicId 오명명을 job_posting_id로 정명
-- ============================================================
CREATE TABLE job_applications (
    id                       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    job_posting_id           VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    applicant_member_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    serial_experience        VARCHAR(30)  NOT NULL,   -- SerialExperience
    assistant_experience     BOOLEAN      NOT NULL DEFAULT FALSE,
    resume_url                VARCHAR(500) NULL,
    review_status             VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',   -- ApplicationReviewStatus
    applied_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_applications_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_job_applications_posting_applicant ON job_applications (job_posting_id, applicant_member_id);  -- 중복 지원 방지
CREATE INDEX idx_job_applications_posting ON job_applications (job_posting_id, applied_at DESC);                       -- 지원자 목록(작성자용)

-- ============================================================
-- team_applications (팀원모집글 지원)
-- ============================================================
CREATE TABLE team_applications (
    id                       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    team_posting_id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    applicant_member_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    serial_experience        VARCHAR(30)  NOT NULL,   -- SerialExperience
    assistant_experience     BOOLEAN      NOT NULL DEFAULT FALSE,
    resume_url                VARCHAR(500) NULL,
    review_status             VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',   -- ApplicationReviewStatus
    applied_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_team_applications_posting FOREIGN KEY (team_posting_id) REFERENCES team_postings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_team_applications_posting_applicant ON team_applications (team_posting_id, applicant_member_id);  -- 중복 지원 방지
CREATE INDEX idx_team_applications_posting ON team_applications (team_posting_id, applied_at DESC);                      -- 지원자 목록(작성자용)
