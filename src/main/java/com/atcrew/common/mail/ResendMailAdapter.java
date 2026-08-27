package com.atcrew.common.mail;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
class ResendMailAdapter implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendMailAdapter.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final MailProperties props;
    private final RestClient restClient;
    private final Counter failureCounter;

    ResendMailAdapter(MailProperties props, MeterRegistry registry) {
        this.props = props;
        this.restClient = RestClient.create();
        // 실패를 예외로 올리지 않고 삼키므로(아래 주석 참고) 로그 외에는 흔적이 남지 않는다 —
        // 비밀번호 재설정 메일이 조용히 전부 실패하는 상황을 감지하려면 지표가 필요하다
        // (docs/design/observability-design.md §6).
        this.failureCounter = Counter.builder("atcrew.mail.send.failure")
                .description("메일 발송 실패 건수")
                .tag("provider", "resend")
                .register(registry);
    }

    // 메일 발송 실패로 API 응답 자체가 깨지면 안 되는 호출부(비밀번호 재설정 요청 — 계정 존재 여부와
    // 무관하게 항상 200)가 있어 best-effort로 로그만 남기고 삼킨다. R2StorageAdapter.triggerWorker와
    // 동일 패턴.
    @Override
    public void send(String to, String subject, String html) {
        try {
            restClient.post().uri(RESEND_API_URL)
                    .header("Authorization", "Bearer " + props.apiKey())
                    .body(Map.of("from", props.fromAddress(), "to", List.of(to),
                            "subject", subject, "html", html))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            failureCounter.increment();
            log.error("메일 발송 실패: subject={}", subject, e);
        }
    }
}
