-- 비밀번호 재설정 토큰 (docs/design/auth-email-custom-redesign.md §7.3).
-- 토큰 원문은 이메일에만 담고 DB에는 SHA-256 해시만 저장한다(유출 시 직접 사용 방지).
-- TTL은 1시간(Figma "비밀번호를 잊으셨나요?" 이메일 문구 확인, 2026-08-12 —
-- 초안 문서(§7.3)의 30분에서 정정. Figma가 정본이므로 이 값을 따른다).
-- 단발성: confirm 성공 시 즉시 삭제(refresh_tokens의 findAndDelete 대체 패턴과 동일).
CREATE TABLE password_reset_tokens (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    token_hash  VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prt_token_hash (token_hash),
    KEY idx_prt_member (member_id),
    KEY idx_prt_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
