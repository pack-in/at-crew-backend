-- 사용자 아이디(handle) 원장 — docs/design/member-handle-design.md §1.1, §4.2
--
-- member_handles는 "이 handle은 이 회원 소유"라는 영구 기록이다. 행을 삭제하지 않으며, 한 번 쓰인 handle은
-- 다른 계정에 재할당되지 않는다(탈퇴 회원 포함). handle PK는 테이블 기본 collation(utf8mb4_unicode_ci)이라
-- 대소문자 무관으로 유일하다. VARCHAR(50)은 일괄 변경 전 옛 handle(규칙 위반, 하이픈 포함)도 리다이렉트용으로
-- 담기 위한 길이다 — 새 규칙(4~14자)은 애플리케이션 HandleValidator가 검증한다.
-- member_id는 같은 모듈 테이블이지만 V1 member 테이블들(member_careers 등)과 같이 FK를 걸지 않는다.
--
-- 순서: (1) 스키마 → (2) 활성 회원 현재 handle 백필 → (3) 탈퇴 회원 handle 복구 → (4) 규칙 위반 handle 일괄 변경.
-- (4)는 운영자 변경 취급이라 handle_changed_at을 건드리지 않는다(사용자는 즉시 변경 가능).
-- 이벤트를 내지 않으므로 배포 직후 POST /internal/search/reindex 전체 재색인이 필요하다(§4.3).

-- ============================================================
-- (1) 스키마
-- ============================================================
CREATE TABLE member_handles (
    handle      VARCHAR(50) NOT NULL,
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (handle),
    KEY idx_mh_member (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 직접 변경 성공 시각. 가입·운영자 변경은 기록하지 않는다(30일 제한 미산정, §1.1).
ALTER TABLE members ADD COLUMN handle_changed_at DATETIME(6) NULL AFTER handle;

-- ============================================================
-- (2) 활성 회원 현재 handle 백필 — 규칙 위반 handle도 넣는다(일괄 변경 후 리다이렉트 대상).
--     members.handle은 같은 collation의 uk_members_handle로 이미 유일하므로 PK 충돌이 없다.
-- ============================================================
INSERT INTO member_handles (handle, member_id, created_at)
SELECT handle, id, created_at
FROM members
WHERE is_active = 1
  AND handle IS NOT NULL;

-- ============================================================
-- (3) 탈퇴 회원 handle 복구 — 탈퇴 시 members.handle을 비워 왔으므로 고정형 포트폴리오
--     snapshot_owner_profile JSON({"name", "handle"} — portfolio OwnerProfileSnapshot)에 남은 값만 살린다.
--     이미 활성 회원이 쓰고 있는 handle이면 INSERT IGNORE로 활성 소유를 유지한다.
--     JSON null을 JSON_UNQUOTE하면 문자열 'null'이 되므로 JSON_TYPE으로 문자열만 고른다.
-- ============================================================
INSERT IGNORE INTO member_handles (handle, member_id, created_at)
SELECT JSON_UNQUOTE(JSON_EXTRACT(p.snapshot_owner_profile, '$.handle')), p.owner_member_id, m.created_at
FROM portfolios p
JOIN members m ON m.id = p.owner_member_id
WHERE m.is_active = 0
  AND p.snapshot_owner_profile IS NOT NULL
  AND JSON_TYPE(JSON_EXTRACT(p.snapshot_owner_profile, '$.handle')) = 'STRING'
  AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(p.snapshot_owner_profile, '$.handle'))) BETWEEN 1 AND 50;

-- ============================================================
-- (4) 규칙 위반 handle 일괄 변경 — §1.2 규칙: [A-Za-z0-9_] 4~14자, 연속 밑줄 금지, 예약어 금지.
--     새 값: 기존 handle의 마지막 '_' 앞부분(없으면 전체)에서 영문·숫자만 남겨 최대 8자(4자 미만이면 'user')
--            + '_' + 숫자 5자리. 결과는 항상 10~14자·밑줄 1개·예약어 아님이라 규칙을 통과한다.
--     문자 검사는 COLLATE utf8mb4_bin으로 한다 — _ci collation이면 REGEXP가 대소문자 무시(PCRE caseless)로
--     동작해 'ſ'(U+017F)·'K'(U+212A) 같은 문자가 [A-Za-z]에 걸린다. '$'는 끝 개행 앞에서도 맞으므로
--     "허용 문자 외 문자가 있는가" + 길이로 나눠 검사한다.
--     예약어 목록은 HandleValidator.RESERVED와 같아야 한다(마이그레이션 테스트가 어긋남을 잡는다).
-- ============================================================
CREATE TABLE v52_handle_renames (
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    base        VARCHAR(8)  NOT NULL,
    new_handle  VARCHAR(50) NULL,
    PRIMARY KEY (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO v52_handle_renames (member_id, base)
SELECT id, CASE WHEN CHAR_LENGTH(stem) >= 4 THEN stem ELSE 'user' END
FROM (
    SELECT id,
           LEFT(REGEXP_REPLACE(
                   (CASE WHEN LOCATE('_', handle) > 0
                         THEN LEFT(handle, CHAR_LENGTH(handle) - LOCATE('_', REVERSE(handle)))
                         ELSE handle END) COLLATE utf8mb4_bin,
                   '[^A-Za-z0-9]', ''), 8) AS stem
    FROM members
    WHERE is_active = 1
      AND handle IS NOT NULL
      AND (CHAR_LENGTH(handle) NOT BETWEEN 4 AND 14
           OR handle COLLATE utf8mb4_bin REGEXP '[^A-Za-z0-9_]'
           OR LOCATE('__', handle) > 0
           OR LOWER(handle) IN ('artwork', 'bookmark', 'community', 'login', 'portfolio', 'pricing',
                                'privacy', 'profile', 'search', 'setting', 'terms', 'trash', 'upload',
                                'billing', '_next', 'admin', 'help', 'about', 'signup', 'settings', 'company'))
) t;

-- 충돌 처리: 후보 추첨 → 충돌 행만 비우기 → 비운 행만 재추첨을 2회 반복한다.
-- 충돌 = 원장(옛 handle·탈퇴 회원 복구분 포함)이나 members.handle(uk_members_handle)에 이미 있는 값, 또는
-- 다른 후보와 같은 값(대소문자 무관, member_id가 큰 쪽을 비운다). 마지막 재추첨 결과는 검사하지 않고
-- 그대로 넣는다 — 그래도 겹치면 아래 원장 INSERT가 PK 위반으로 실패해 마이그레이션 실패로 드러난다.
UPDATE v52_handle_renames
SET new_handle = CONCAT(base, '_', LPAD(FLOOR(RAND() * 100000), 5, '0'));

UPDATE v52_handle_renames r
SET r.new_handle = NULL
WHERE r.new_handle IN (SELECT handle FROM member_handles)
   OR r.new_handle IN (SELECT handle FROM members WHERE handle IS NOT NULL)
   OR EXISTS (SELECT 1 FROM v52_handle_renames o
              WHERE o.new_handle = r.new_handle AND o.member_id < r.member_id);

-- 재추첨 1회차
UPDATE v52_handle_renames
SET new_handle = CONCAT(base, '_', LPAD(FLOOR(RAND() * 100000), 5, '0'))
WHERE new_handle IS NULL;

UPDATE v52_handle_renames r
SET r.new_handle = NULL
WHERE r.new_handle IN (SELECT handle FROM member_handles)
   OR r.new_handle IN (SELECT handle FROM members WHERE handle IS NOT NULL)
   OR EXISTS (SELECT 1 FROM v52_handle_renames o
              WHERE o.new_handle = r.new_handle AND o.member_id < r.member_id);

-- 재추첨 2회차(마지막 — 검사 없이 아래 INSERT의 PK가 최종 판정)
UPDATE v52_handle_renames
SET new_handle = CONCAT(base, '_', LPAD(FLOOR(RAND() * 100000), 5, '0'))
WHERE new_handle IS NULL;

-- 새 handle을 원장에 넣고(옛 handle 행은 (2)에서 들어가 그대로 남는다) members.handle에 반영한다.
INSERT INTO member_handles (handle, member_id, created_at)
SELECT new_handle, member_id, UTC_TIMESTAMP(6)
FROM v52_handle_renames;

UPDATE members m
JOIN v52_handle_renames r ON r.member_id = m.id
SET m.handle = r.new_handle;

DROP TABLE v52_handle_renames;
