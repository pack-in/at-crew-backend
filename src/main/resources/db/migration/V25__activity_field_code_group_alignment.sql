-- 활동 분야 코드 그룹 정렬 — 정책 데이터구조-R03("활동 분야는 작가·기업이 동일 코드 그룹을 공유한다")
--
-- 1) member.ActivityField.PUBLISHED_MANGA → PRINT_COMIC 로 이름 통일
--    (artwork.ArtworkField·company.ActivityField와 같은 상수 이름을 쓰게 한다. 홈 화면이 작품 탭과
--     작가 탭에 같은 칩 행을 쓰므로 — 기획서 홈-R01 — 라벨이 같으면 API 값도 같아야 한다.)
-- 2) company 활동 분야를 복수(연결 테이블) → 단일(컬럼)로 전환
--    (피그마 5779:32101·기획서 마이페이지_기업-R07 "활동 분야(4)는 단일 칩")

-- ------------------------------------------------------------
-- 1) member_activity_fields 값 이름 통일
--    PK가 (member_id, activity_field)지만 PRINT_COMIC은 member 쪽에 존재한 적이 없는 값이라
--    UPDATE로 인한 PK 충돌이 발생하지 않는다.
-- ------------------------------------------------------------
UPDATE member_activity_fields
SET activity_field = 'PRINT_COMIC'
WHERE activity_field = 'PUBLISHED_MANGA';

-- 정본 밖의 값이 남아 있으면 @Enumerated(STRING) 역매핑이 깨지므로 제거한다.
DELETE FROM member_activity_fields
WHERE activity_field NOT IN ('ILLUSTRATION', 'WEBTOON', 'PRINT_COMIC', 'ANIMATION');

-- ------------------------------------------------------------
-- 2) company 활동 분야 단일화
--    기존 company_activity_fields의 값 중 정본 4종에 드는 것 하나만 companies.activity_field로 옮긴다.
--    WEB_NOVEL·OTHER는 정본에서 빠진 값이라 이관하지 않는다(미입력으로 남는다).
--    복수로 저장돼 있던 기업은 enum 선언 순서상 가장 앞선 값 하나만 남는다 — 단일 선택 화면으로
--    바뀌는 이상 어떤 값을 남겨도 손실이 발생하며, 결정적(deterministic) 규칙을 택한다.
-- ------------------------------------------------------------
ALTER TABLE companies ADD COLUMN activity_field VARCHAR(30) NULL AFTER company_type;

UPDATE companies c
SET c.activity_field = (
    SELECT f.activity_field
    FROM company_activity_fields f
    WHERE f.company_id = c.id
      AND f.activity_field IN ('ILLUSTRATION', 'WEBTOON', 'PRINT_COMIC', 'ANIMATION')
    ORDER BY FIELD(f.activity_field, 'ILLUSTRATION', 'WEBTOON', 'PRINT_COMIC', 'ANIMATION')
    LIMIT 1
);

DROP TABLE company_activity_fields;
