-- 작가 프로필 "구직 정보" 탭과 기본 정보 탭의 미구현 항목을 추가한다.
-- 근거: 기획서 마이페이지_작가-R24(구직 정보 수정)·R23(기본 정보 수정), 피그마 4971:25431.
--
-- 값 집합은 member 모듈이 자체 정의한다. recruit 구직글에도 담당 업무·장르·피드백 방식·작업 스타일이
-- 있지만 값 집합이 서로 다르다(피드백 방식 4종 vs 7종, 작업 스타일 4종 vs 3종, 채용 형태 4종 vs 5종,
-- 담당 업무는 작화·식자 유무 차이). 모듈 간 직접 의존 금지 원칙에 따라 별도 정의하며, 두 값 집합의
-- 통일은 별도 과제로 남긴다.

-- ── 단일 선택 컬럼 ──
ALTER TABLE members
    ADD COLUMN work_pace                 VARCHAR(30) NULL,
    ADD COLUMN available_start_period    VARCHAR(30) NULL,
    ADD COLUMN desired_work_location     VARCHAR(30) NULL,
    ADD COLUMN desired_minimum_guarantee VARCHAR(30) NULL,
    ADD COLUMN desired_annual_salary     VARCHAR(30) NULL;

-- ── 복수 선택 연결 테이블 ──
CREATE TABLE member_drawing_styles (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_desired_roles (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value),
    -- 작가 찾기 노출 조건(마이페이지_작가-R08)이 "희망 담당 업무 미입력"을 걸러내므로 역방향 조회가 필요하다.
    KEY idx_mdr_value (value, member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_desired_genres (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value),
    KEY idx_mdg_value (value, member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_desired_employment_types (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE member_feedback_preferences (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(30) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ── 직접입력 태그 (업로드-R13) ──
-- 항목마다 테이블을 나누지 않고 tag_type을 함께 저장한다 — 저장 규칙(10자·중복 불가·공백 제거)이
-- 전 항목 공통이라 분리해서 얻는 것이 없다. 값은 공통 코드로 등록하지 않는다(정책 데이터구조-R04).
CREATE TABLE member_custom_tags (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    tag_type  VARCHAR(30) NOT NULL,
    value     VARCHAR(10) NOT NULL,
    PRIMARY KEY (member_id, tag_type, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
