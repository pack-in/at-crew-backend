package com.atcrew.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.atcrew.common.logging.LogMask;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.logback.SentryAppender;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import jakarta.annotation.PostConstruct;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentry 연동(docs/design/observability-design.md §5).
 *
 * <p><b>스타터를 쓰지 않는 이유.</b> {@code sentry-spring-boot-starter-jakarta}는 Spring Boot 4를
 * 지원하지 않는다. 오토컨피그가 Boot 3에만 있던 {@code org.springframework.boot.web.client.RestClientCustomizer}를
 * 참조해, DSN이 주입되는 순간 {@code ClassNotFoundException}으로 컨텍스트 초기화가 실패한다.
 * 8.16.0 기준 Boot 4용 아티팩트가 없어 SDK를 직접 초기화하고 logback 어펜더를 손으로 붙인다(이슈 #62).
 * 스타터가 제공하던 것 중 이 서비스가 실제로 쓰던 기능은 어펜더 연결과 옵션 바인딩뿐이라 손실이 없다.
 *
 * <p><b>PII 제거.</b> 에러 추적은 스택트레이스와 예외 메시지를 외부로 보내는 일이라, 이메일이나
 * 토큰이 섞여 나가는 경로를 SDK 설정만으로 막을 수 없다. 전송 직전에 한 번 더 훑는다 —
 * {@code sendDefaultPii=false}는 요청 헤더·쿠키만 막을 뿐, 우리 코드가 만든 메시지 본문은 손대지 않는다.
 */
@Configuration
@EnableConfigurationProperties(SentryProperties.class)
class SentryConfig {

    private static final Logger log = LoggerFactory.getLogger(SentryConfig.class);

    static final String APPENDER_NAME = "SENTRY";

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // JWT(access/refresh 토큰)와 그 형태를 띤 외부 토큰. 로그에 실려 나가면 그대로 계정 탈취로 이어진다.
    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    private final SentryProperties properties;

    SentryConfig(SentryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (!properties.enabled()) {
            // 로컬·테스트 기본값. prod는 application-prod.yml이 SENTRY_DSN을 강제한다.
            log.debug("sentry.dsn이 비어 있어 Sentry를 초기화하지 않는다");
            return;
        }

        SentryOptions options = buildOptions();
        Sentry.init(options);
        attachLogbackAppender(options);
        log.info("Sentry 초기화 완료: environment={}", properties.environment());
    }

    private SentryOptions buildOptions() {
        SentryOptions options = new SentryOptions();
        options.setDsn(properties.dsn());
        options.setEnvironment(properties.environment());
        options.setSendDefaultPii(properties.sendDefaultPii());
        options.setTracesSampleRate(properties.tracesSampleRate());
        // 미설정(0)이면 SDK 기본값을 그대로 둔다 — 0으로 덮어쓰면 브레드크럼이 통째로 사라진다.
        if (properties.maxBreadcrumbs() > 0) {
            options.setMaxBreadcrumbs(properties.maxBreadcrumbs());
        }
        // MDC의 요청 식별자를 태그로 올려 Loki 로그와 상호 참조할 수 있게 한다.
        if (properties.contextTags() != null) {
            properties.contextTags().forEach(options::addContextTag);
        }
        options.setBeforeSend((event, hint) -> {
            scrub(event);
            return event;
        });
        // 브레드크럼은 직전 로그 본문을 그대로 담아 나간다 — 같은 기준으로 훑는다.
        options.setBeforeBreadcrumb((breadcrumb, hint) -> {
            breadcrumb.setMessage(mask(breadcrumb.getMessage()));
            return breadcrumb;
        });
        return options;
    }

    /**
     * 스타터가 대신 해 주던 일. 루트 로거에 Sentry 어펜더를 붙여 ERROR 로그를 이슈로 만든다.
     *
     * <p>{@code logback-spring.xml}을 두는 방법도 있지만, 그러면 logback 설정 전체를 우리가 떠안게 되어
     * {@code logging.structured.format.console: ecs}(prod JSON 로깅)까지 직접 재현해야 한다.
     * 어펜더 하나만 추가하면 되는 일이라 프로그래밍 방식으로 붙인다.
     */
    private void attachLogbackAppender(SentryOptions options) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            log.warn("logback이 아니라 Sentry 어펜더를 붙이지 못했다: {}", factory.getClass().getName());
            return;
        }

        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        // logback의 LoggerContext는 JVM 전역이지만 이 메서드는 Spring 컨텍스트마다 실행된다.
        // 테스트 JVM처럼 컨텍스트가 여럿 뜨는 환경에서는 어펜더가 계속 쌓여, ERROR 로그 하나가
        // 컨텍스트 수만큼 중복 전송되고 전송 큐·스레드도 그만큼 늘어난다. 붙이기 전에 먼저 걷어낸다.
        Appender<ILoggingEvent> existing = root.getAppender(APPENDER_NAME);
        if (existing != null) {
            root.detachAppender(existing);
            existing.stop();
        }

        SentryAppender appender = new SentryAppender();
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.setOptions(options);
        SentryProperties.Logging logging = properties.loggingOrDefault();
        appender.setMinimumEventLevel(level(logging.minimumEventLevel(), Level.ERROR));
        appender.setMinimumBreadcrumbLevel(level(logging.minimumBreadcrumbLevel(), Level.WARN));
        appender.start();
        root.addAppender(appender);
    }

    private static Level level(String configured, Level fallback) {
        return configured == null || configured.isBlank() ? fallback : Level.toLevel(configured, fallback);
    }

    private void scrub(SentryEvent event) {
        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(mask(message.getMessage()));
            message.setFormatted(mask(message.getFormatted()));
        }
        if (event.getExceptions() != null) {
            for (SentryException e : event.getExceptions()) {
                e.setValue(mask(e.getValue()));
            }
        }
    }

    static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher emails = EMAIL.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (emails.find()) {
            emails.appendReplacement(sb, Matcher.quoteReplacement(LogMask.email(emails.group())));
        }
        emails.appendTail(sb);
        return JWT.matcher(sb.toString()).replaceAll("<redacted-token>");
    }
}
