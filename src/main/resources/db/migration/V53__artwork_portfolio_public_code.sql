-- 작품·공유 포트폴리오 6자 공개 코드 — docs/design/member-handle-design.md §3, §4.2-4
--
-- 공개 URL /{handle}/artwork/{code}, /{handle}/portfolio/{code}의 {code}. [a-z0-9] 6자이며 작가(소유자) 안에서만
-- 유일하다 — 작품과 포트폴리오는 경로로 갈리므로 서로 같은 코드여도 된다. 원장 없이 엔티티 컬럼에만 두므로 행이
-- 완전히 삭제되면 재사용될 수 있다(휴지통 작품은 행이 남아 코드도 유지된다).
-- 작가 페이지 포트폴리오(ARTIST_PAGE)는 주소가 /{handle}이라 코드가 없다(NULL) — UNIQUE는 NULL을 중복으로 보지
-- 않으므로 (owner_member_id, public_code) 제약과 공존한다.
-- 신규 발급은 애플리케이션 PublicCodeGenerator(SecureRandom)가 한다. 이 백필은 이벤트를 내지 않으므로 배포 직후
-- POST /internal/search/reindex 전체 재색인이 필요하다(§4.3).
--
-- 순서: (1) 컬럼 추가 → (2) 전 행 추첨 → (3) 작가 내 충돌 행만 재추첨 2회 → (4) 제약.
-- 마지막 재추첨 결과는 검사하지 않는다 — 그래도 겹치면 (4)의 UNIQUE 생성이 실패해 마이그레이션 실패로 드러난다.
-- 작가당 경우의 수 36^6(약 21억) 대비 운영 규모(작품 30·작가 최대 9, SHARED 포트폴리오 7)라 실질 충돌은 없다.

-- ============================================================
-- (1) 컬럼 추가
-- ============================================================
ALTER TABLE artworks   ADD COLUMN public_code CHAR(6) CHARACTER SET latin1 COLLATE latin1_bin NULL AFTER author_id;
ALTER TABLE portfolios ADD COLUMN public_code CHAR(6) CHARACTER SET latin1 COLLATE latin1_bin NULL AFTER share_slug;

-- ============================================================
-- (2) 전 행 추첨 — RAND()는 행마다, 호출마다 새로 평가된다.
--     작품은 상태 무관 전부(휴지통 DELETED 포함), 포트폴리오는 SHARED만.
-- ============================================================
UPDATE artworks
SET public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1));

UPDATE portfolios
SET public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1))
WHERE kind = 'SHARED';

-- ============================================================
-- (3) 작가 내 충돌 행만 재추첨 2회 — 같은 (작가, 코드) 묶음에서 id가 가장 작은 행은 남기고 나머지만 다시 뽑는다.
--     ROW_NUMBER() 파생 테이블은 병합되지 않고 먼저 계산되므로 갱신 대상 테이블을 같은 문에서 읽어도 된다.
-- ============================================================
-- 재추첨 1회차
UPDATE artworks a
JOIN (SELECT id
      FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY author_id, public_code ORDER BY id) AS rn
            FROM artworks) ranked
      WHERE rn > 1) dup ON dup.id = a.id
SET a.public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1));

UPDATE portfolios p
JOIN (SELECT id
      FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY owner_member_id, public_code ORDER BY id) AS rn
            FROM portfolios
            WHERE public_code IS NOT NULL) ranked
      WHERE rn > 1) dup ON dup.id = p.id
SET p.public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1));

-- 재추첨 2회차(마지막 — 검사 없이 아래 UNIQUE가 최종 판정)
UPDATE artworks a
JOIN (SELECT id
      FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY author_id, public_code ORDER BY id) AS rn
            FROM artworks) ranked
      WHERE rn > 1) dup ON dup.id = a.id
SET a.public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1));

UPDATE portfolios p
JOIN (SELECT id
      FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY owner_member_id, public_code ORDER BY id) AS rn
            FROM portfolios
            WHERE public_code IS NOT NULL) ranked
      WHERE rn > 1) dup ON dup.id = p.id
SET p.public_code = CONCAT(
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1),
        SUBSTRING('abcdefghijklmnopqrstuvwxyz0123456789', FLOOR(RAND() * 36) + 1, 1));

-- ============================================================
-- (4) 제약 — 작품은 모두 코드를 가지므로 NOT NULL, 포트폴리오는 ARTIST_PAGE 때문에 NULL 허용.
-- ============================================================
ALTER TABLE artworks
    MODIFY COLUMN public_code CHAR(6) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ADD UNIQUE KEY uk_artworks_author_code (author_id, public_code);

ALTER TABLE portfolios
    ADD UNIQUE KEY uk_pf_owner_code (owner_member_id, public_code);
