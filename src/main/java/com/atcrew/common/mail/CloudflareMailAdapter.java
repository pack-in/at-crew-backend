package com.atcrew.common.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare Email Service(Email Sending REST API)로 메일을 보낸다.
 *
 * <p>발신 도메인 mail.at-crew.com은 팀 Cloudflare 계정에 Email Sending이 켜져 있고 DKIM·SPF·DMARC가 갖춰져 있다.
 * 2026-10-20 Resend에서 옮겼다 — Resend는 계정이 만들어진 적이 없어 운영 메일이 한 통도 나가지 않았고,
 * 이미 쓰는 Cloudflare에 발송 설정이 되어 있었다.
 */
@Component
class CloudflareMailAdapter implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(CloudflareMailAdapter.class);
    private static final String API_BASE_URL = "https://api.cloudflare.com/client/v4";

    // 비밀번호 재설정 요청 처리 중이나 이벤트 리스너 트랜잭션 안에서 기다리므로, 느린 응답에 오래 붙잡히지 않게 짧게 둔다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final MailProperties props;
    private final RestClient restClient;
    private final Counter failureCounter;

    @Autowired
    CloudflareMailAdapter(MailProperties props, MeterRegistry registry) {
        this(props, registry, API_BASE_URL);
    }

    // 테스트가 로컬 서버로 요청을 받기 위한 생성자.
    CloudflareMailAdapter(MailProperties props, MeterRegistry registry, String apiBaseUrl) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + props.cloudflare().apiToken())
                .build();
        // 실패를 예외로 올리지 않고 삼키므로(아래 주석 참고) 로그 외에는 흔적이 남지 않는다 —
        // 메일이 조용히 전부 실패하는 상황을 알람으로 잡으려면 지표가 필요하다(docs/design/observability-design.md §6).
        this.failureCounter = Counter.builder("atcrew.mail.send.failure")
                .description("메일 발송 실패 건수")
                .tag("provider", "cloudflare")
                .register(registry);
    }

    // 메일 발송 실패로 API 응답 자체가 깨지면 안 되는 호출부(비밀번호 재설정 요청 — 계정 존재 여부와
    // 무관하게 항상 같은 응답)가 있어 best-effort로 로그만 남기고 삼킨다.
    @Override
    public void send(String to, String subject, String html) {
        try {
            SendResponse response = restClient.post()
                    .uri("/accounts/{accountId}/email/sending/send", props.cloudflare().accountId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from(),
                            "to", to,
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .body(SendResponse.class);
            if (response == null || !response.success() || response.bounced()) {
                failureCounter.increment();
                log.error("메일 발송 실패: subject={}, errors={}, bounced={}", subject,
                        response == null ? null : response.errors(),
                        response != null && response.bounced());
            }
        } catch (Exception e) {
            failureCounter.increment();
            log.error("메일 발송 실패: subject={}", subject, e);
        }
    }

    private Map<String, String> from() {
        return props.fromName() == null || props.fromName().isBlank()
                ? Map.of("address", props.fromAddress())
                : Map.of("address", props.fromAddress(), "name", props.fromName());
    }

    /** 2xx여도 success=false이거나 영구 반송이면 실패로 본다. 수신자 주소는 로그에 남기지 않는다. */
    record SendResponse(boolean success, List<Map<String, Object>> errors, Result result) {
        boolean bounced() {
            return result != null && result.permanentBounces() != null && !result.permanentBounces().isEmpty();
        }
    }

    record Result(@JsonProperty("permanent_bounces") List<String> permanentBounces) { }
}
