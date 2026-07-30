-- company 모듈 스키마 (기업 마이페이지 — 기업 프로필·경력).
-- 설계 근거: docs/design/company-profile-module-design.md §7
-- id 컬럼은 애플리케이션이 생성하는 UUIDv7 문자열. ASCII 전용이므로 latin1_bin으로 인덱스 키를 줄인다.
-- companies.member_id는 member 모듈을 넘는 참조이므로 FK를 걸지 않는다(V1과 동일한 FK 정책).

CREATE TABLE companies (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id                   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    company_name                VARCHAR(16)  NULL,
    contact                     VARCHAR(255) NULL,
    sns                         VARCHAR(255) NULL,
    recruit_status              VARCHAR(30)  NOT NULL DEFAULT 'PREPARING',
    company_type                VARCHAR(30)  NULL,
    has_business_registration   TINYINT(1)   NOT NULL DEFAULT 0,
    -- 기업 인증 완료 여부 — 로드맵 1번(본인/기업 인증 시스템) 연동 전까지 항상 0, API로 변경 불가
    verified                    TINYINT(1)   NOT NULL DEFAULT 0,
    version                     BIGINT       NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 1인 1기업 — 서비스 레벨 검증의 최종 방어선
    UNIQUE KEY uk_companies_member (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_activity_fields (
    company_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    activity_field  VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_id, activity_field)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_active_regions (
    company_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_careers (
    id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    company_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    work_title    VARCHAR(255) NULL,
    start_date    DATE NULL,
    end_date      DATE NULL,
    ongoing       TINYINT(1) NOT NULL DEFAULT 0,
    description   VARCHAR(200) NULL,
    PRIMARY KEY (id),
    KEY idx_cc_company (company_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
