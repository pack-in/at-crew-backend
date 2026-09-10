-- 비밀번호 재설정을 링크 토큰에서 이메일 OTP 코드 방식으로 전환 (Figma LAITEU node 6626:4159, 이슈 #151).
-- 같은 테이블을 상태 전이로 재사용한다: verified_at이 NULL이면 아직 코드만 발급된 상태(token_hash =
-- HMAC-SHA256(코드)), verified_at이 채워지면 코드 검증이 끝나고 재설정 세션 토큰이 발급된 상태
-- (token_hash = SHA-256(세션 토큰)로 같은 컬럼을 재사용). 신규 테이블 없이 컬럼 하나만 추가한다.
ALTER TABLE password_reset_tokens
    ADD COLUMN verified_at DATETIME(6) NULL AFTER expires_at;
