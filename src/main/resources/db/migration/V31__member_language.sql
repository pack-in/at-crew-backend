-- 주 사용 언어(members.primary_language)와 게시물 노출 언어(member_post_languages)를 추가한다.
-- 근거: 정본 기능·화면 명세 로그인-R19(가입 완료 화면에서 주 사용 언어 4개 중 필수 선택, 가입 후 변경 불가),
--       정책 설정-R14(설정에서 노출 게시물 언어 복수 선택, 주 사용 언어 칩 해제 불가),
--       정책 로그인-R16(주 사용 언어가 다른 사용자 간 계정·게시글 기본 비노출).
--
-- primary_language를 NULL 허용으로 두는 이유: 기존 회원은 가입 시 언어를 고른 적이 없다. 임의 값으로
-- 백필하면 그 회원의 계정·게시글이 갑자기 특정 언어 세그먼트에만 보이게 되므로, NULL로 남기고
-- 조회 필터에서 "항상 노출"로 폴백한다(docs/design/settings-i18n-design.md §5.1 폴백 규칙).
-- 신규 가입만 애플리케이션에서 필수로 강제한다.

ALTER TABLE members
    ADD COLUMN primary_language VARCHAR(10) NULL AFTER country_code;

CREATE TABLE member_post_languages (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(10) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 작가 찾아보기(/api/community/authors)의 언어 세그먼트 필터용 — 기존 idx_members_search_latest
-- (is_active, employment_status, updated_at DESC)에 primary_language를 끼워 넣은 확장 인덱스다.
-- 언어 조건을 인덱스 안에서 거르고 나머지는 기존과 동일한 접근 경로를 쓴다.
CREATE INDEX idx_members_lang_search
    ON members (is_active, employment_status, primary_language, updated_at DESC);
