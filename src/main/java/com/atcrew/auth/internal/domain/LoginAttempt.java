package com.atcrew.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 로그인 실패 횟수 집계 행 (docs/design/mariadb-migration-design.md §3.3.4).
 *
 * <p>이 테이블은 원자성 보장을 위해 전부 단일 문장 쿼리(INSERT ... ON DUPLICATE KEY UPDATE, 조건부 DELETE)로만
 * 조작한다 — 엔티티 인스턴스를 로드해 수정하는 경로가 없으므로 도메인 행위·접근자를 두지 않는다.
 * 엔티티로 선언하는 이유는 리포지토리 매핑과 {@code ddl-auto: validate} 스키마 검증 대상에 포함시키기 위함이다.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    // 시도 주체 키 — "email:{이메일}" 또는 "ip:{IP}" 형태
    @Id
    @Column(name = "attempt_key")
    private String attemptKey;

    // 현재 윈도우 내 누적 실패 횟수
    private int failCount;

    // 윈도우 시작 시각 — 고정 윈도우(10분) 시맨틱이라 윈도우가 살아있는 동안 갱신되지 않는다
    private Instant firstFailedAt;

    protected LoginAttempt() {}
}
