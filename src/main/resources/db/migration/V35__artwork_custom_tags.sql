-- 작품 직접입력 값 저장 (이슈 #98, 업로드-R13). 담당 업무·장르는 member_custom_tags(V30)와
-- 동일한 패턴 — 항목마다 테이블을 나누지 않고 tag_type을 함께 저장한다(저장 규칙이 전 항목 공통이라
-- 분리해서 얻는 것이 없다, 정책 데이터구조-R04). 소재 대상은 artwork_materials.targets가 이미
-- JSON 컬럼이라 같은 패턴을 쓰지 못해 옆에 custom_targets JSON 컬럼을 나란히 둔다.

CREATE TABLE artwork_custom_tags (
    artwork_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    tag_type   VARCHAR(30) NOT NULL,
    value      VARCHAR(10) NOT NULL,
    PRIMARY KEY (artwork_id, tag_type, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE artwork_materials
    ADD COLUMN custom_targets JSON NULL AFTER targets;
