package com.atcrew.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Sentry 설정. Spring Boot 스타터를 쓰지 않으므로({@link SentryConfig} 클래스 주석 참고)
 * 프로퍼티 바인딩과 초기화를 직접 한다.
 *
 * <p>키 이름은 스타터가 쓰던 것과 동일하게 유지한다 — {@code application.yml}의 기존 블록과
 * {@code docs/design/observability-design.md} §5를 그대로 따르기 위해서다.
 *
 * @param dsn               비어 있으면 Sentry를 초기화하지 않는다(로컬·테스트 기본값)
 * @param environment       Sentry 이슈의 environment 태그
 * @param sendDefaultPii    요청 헤더·쿠키·IP 등 SDK가 자동으로 붙이는 개인정보 전송 여부
 * @param tracesSampleRate  분산 추적 표본율. 단일 서비스라 0.0으로 끈다
 * @param maxBreadcrumbs    이벤트에 함께 실어 보낼 직전 로그 개수
 * @param contextTags       태그로 승격할 MDC 키
 * @param logging           로그 레벨별 전송 기준
 */
@ConfigurationProperties(prefix = "sentry")
record SentryProperties(String dsn, String environment, boolean sendDefaultPii, double tracesSampleRate,
                        int maxBreadcrumbs, List<String> contextTags, Logging logging) {

    /**
     * @param minimumEventLevel      이 레벨 이상을 Sentry 이슈로 만든다
     * @param minimumBreadcrumbLevel 이 레벨 이상을 브레드크럼으로 남긴다
     */
    record Logging(String minimumEventLevel, String minimumBreadcrumbLevel) {
    }

    boolean enabled() {
        return dsn != null && !dsn.isBlank();
    }

    /** {@code sentry.logging} 블록이 없어도 어펜더 기본값으로 동작하게 한다. */
    Logging loggingOrDefault() {
        return logging != null ? logging : new Logging(null, null);
    }
}
