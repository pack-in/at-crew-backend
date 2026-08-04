CREATE TABLE media_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_type VARCHAR(30) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    original_key VARCHAR(500) NOT NULL,
    thumb_key VARCHAR(500) NULL,
    thumb_adult_key VARCHAR(500) NULL,
    original_avif_key VARCHAR(500) NULL,
    variant_profile VARCHAR(30) NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_ma_owner_order UNIQUE (owner_type, owner_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ma_owner ON media_assets (owner_type, owner_id, ordinal);
CREATE INDEX idx_ma_retry ON media_assets (processing_status, updated_at);

CREATE TABLE orphaned_media_keys (
    id VARCHAR(36) PRIMARY KEY,
    keys_json JSON NOT NULL,
    marked_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
