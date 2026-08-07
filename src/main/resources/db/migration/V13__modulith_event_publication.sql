-- Spring Modulith 이벤트 레지스트리(docs/design/mariadb-migration-design.md §3.8).
-- MongoDB에 저장하던 event_publication을 MariaDB로 이관한다.
--
-- 아래 DDL은 손으로 작성한 것이 아니라 spring-modulith-events-jdbc-2.0.6.jar의 공식 리소스
-- org/springframework/modulith/events/jdbc/schemas/v2/schema-mariadb.sql 을 그대로 복사한 것이다.
-- v2를 쓰는 이유: spring.modulith.events.jdbc.use-legacy-structure 기본값이 false이므로
-- Modulith 2.0.6은 v2 스키마(STATUS/COMPLETION_ATTEMPTS/LAST_RESUBMISSION_DATE 포함)를 기대한다.
-- EVENT_PUBLICATION_ARCHIVE는 완료 모드가 ARCHIVE일 때만 필요한데, 기본값 UPDATE를 쓰므로 생성하지 않는다.
--
-- 스키마의 유일한 진실은 이 Flyway 마이그레이션이다 — Modulith 자동 생성은
-- application.yml의 spring.modulith.events.jdbc.schema-initialization.enabled: false 로 꺼져 있다.
-- Modulith 버전 업그레이드로 공식 스키마가 바뀌면 새 마이그레이션으로 명시 반영할 것.
--
-- ID는 MariaDB 10.7+의 네이티브 UUID 타입이 아니라 공식 스키마 그대로 VARCHAR(36)이다(§3.8 경고).

CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION
(
  ID                     VARCHAR(36) NOT NULL,
  LISTENER_ID            VARCHAR(512) NOT NULL,
  EVENT_TYPE             VARCHAR(512) NOT NULL,
  SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
  PUBLICATION_DATE       TIMESTAMP(6) NOT NULL,
  COMPLETION_DATE        TIMESTAMP(6) DEFAULT NULL NULL,
  STATUS                 VARCHAR(20),
  COMPLETION_ATTEMPTS    INT,
  LAST_RESUBMISSION_DATE TIMESTAMP(6),
  PRIMARY KEY (ID),
  INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
