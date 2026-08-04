-- recruit 게시글 이미지 자식 테이블 (docs/design/media-module-design.md §10.1)
--
-- 기존 thumbnail_image(단일 VARCHAR) / reference_images(JSON 리스트) 컬럼은 그대로 둔다(§10.4, §11).
-- 자식 테이블은 media 모듈이 만들어내는 변환본(thumb / original AVIF) 키를 담는 자리이며,
-- 응답 조립은 이 테이블을 우선 사용하고 자식 행이 없는 과거 데이터만 기존 컬럼으로 폴백한다.
--
-- thumb_adult_key 컬럼은 만들지 않는다 — recruit은 variantProfile=STANDARD 고정이라
-- 성인물 blur 썸네일 변환 자체를 요청하지 않는다(§3).

CREATE TABLE job_posting_images (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    posting_id        VARCHAR(36)  NOT NULL,
    role              VARCHAR(20)  NOT NULL,   -- THUMBNAIL / REFERENCE
    ordinal           INT          NOT NULL,   -- THUMBNAIL=0, REFERENCE=1..n (media_assets.ordinal과 동일 축)
    original_key      VARCHAR(500) NOT NULL,
    thumb_key         VARCHAR(500) NULL,
    original_avif_key VARCHAR(500) NULL,
    processing_status VARCHAR(30)  NOT NULL,   -- MediaProcessingStatus
    CONSTRAINT uk_jpi_posting_ordinal UNIQUE (posting_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_posting_images (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    posting_id        VARCHAR(36)  NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    ordinal           INT          NOT NULL,
    original_key      VARCHAR(500) NOT NULL,
    thumb_key         VARCHAR(500) NULL,
    original_avif_key VARCHAR(500) NULL,
    processing_status VARCHAR(30)  NOT NULL,
    CONSTRAINT uk_tpi_posting_ordinal UNIQUE (posting_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 구직글은 썸네일 없이 참고 이미지만 쓰지만(§2.3), 스키마는 세 테이블을 동일하게 유지한다.
CREATE TABLE job_seeking_post_images (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    posting_id        VARCHAR(36)  NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    ordinal           INT          NOT NULL,
    original_key      VARCHAR(500) NOT NULL,
    thumb_key         VARCHAR(500) NULL,
    original_avif_key VARCHAR(500) NULL,
    processing_status VARCHAR(30)  NOT NULL,
    CONSTRAINT uk_jspi_posting_ordinal UNIQUE (posting_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- posting 레벨 이미지 처리 상태 (§10.2)
-- 발행 상태(status: DRAFT/PENDING/PUBLISHED/CLOSED/DELETED)와는 독립된 축이다 — 합치지 않는다.
-- 기존 행은 처리 대기 중인 이미지가 없으므로 READY로 채운다.
ALTER TABLE job_postings      ADD COLUMN image_processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE team_postings     ADD COLUMN image_processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE job_seeking_posts ADD COLUMN image_processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';
