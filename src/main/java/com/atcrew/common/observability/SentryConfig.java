package com.atcrew.common.observability;

import com.atcrew.common.logging.LogMask;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentry로 나가는 이벤트에서 PII를 제거한다(docs/design/observability-design.md §5).
 *
 * <p>에러 추적은 스택트레이스와 예외 메시지를 외부로 보내는 일이라, 이메일이나 토큰이 섞여 나가는
 * 경로를 SDK 설정만으로 막을 수 없다. 전송 직전에 한 번 더 훑는다 — {@code sendDefaultPii=false}는
 * 요청 헤더·쿠키만 막을 뿐, 우리 코드가 만든 메시지 본문은 손대지 않는다.
 */
@Configuration
class SentryConfig {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // JWT(access/refresh 토큰)와 그 형태를 띤 외부 토큰. 로그에 실려 나가면 그대로 계정 탈취로 이어진다.
    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    @Bean
    Sentry.OptionsConfiguration<SentryOptions> sentryOptions() {
        return options -> {
            options.setBeforeSend((event, hint) -> {
                scrub(event);
                return event;
            });
            // 브레드크럼은 직전 로그 본문을 그대로 담아 나간다 — 같은 기준으로 훑는다.
            options.setBeforeBreadcrumb((breadcrumb, hint) -> {
                breadcrumb.setMessage(mask(breadcrumb.getMessage()));
                return breadcrumb;
            });
        };
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
