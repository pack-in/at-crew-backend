-- 비밀번호 변경(설정 화면)에 2단계 재인증 흐름을 도입한다 (Figma LAITEU node 6631:71053, 이슈 #152).
-- 1단계(현재 비밀번호 확인) 성공 시 이 표에 짧은 수명의 토큰을 발급하고, 2단계(새 비밀번호 입력)에서
-- 그 토큰을 소비한다. password_reset_tokens와 동일 패턴이지만 용도가 달라(로그인 상태의 재인증 vs
-- 로그아웃 상태의 계정 복구) 별도 테이블로 둔다. 원문은 응답에만 담고 여기엔 SHA-256 해시만 저장한다.
CREATE TABLE password_reauth_tokens (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    token_hash  VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prat_token_hash (token_hash),
    KEY idx_prat_member (member_id),
    KEY idx_prat_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
