package com.atcrew.analytics.internal.config;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostHog 수집 API({@code POST /batch/})로 이벤트를 동기 전송한다(plans/261019-posthog-server-delivery).
 *
 * <p>공식 SDK(posthog-server)를 쓰지 않는 이유: SDK는 메모리 큐에 넣고 바로 돌아오며 전송 실패를 내부에서
 * 삼킨다. 그러면 리스너가 늘 성공으로 끝나 Modulith가 발행 기록을 완료 처리하고, 종료 시에는 큐를 비우지 않아
 * 배포마다 이벤트가 사라진다. 여기서는 응답을 받을 때까지 기다리고 실패하면 예외를 던진다.
 */
public class PostHogCaptureClient {

    private final RestClient restClient;
    private final String apiKey;

    public PostHogCaptureClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    /**
     * 이벤트 하나를 보낸다. 2xx가 아니거나 연결·타임아웃 오류면 {@link RestClientException}을 던진다.
     *
     * @param event      이벤트명
     * @param distinctId PostHog distinct_id
     * @param properties 전송할 속성(null 값이 없어야 한다)
     * @param timestamp  사건 발생 시각
     * @param uuid       이벤트 UUID — PostHog는 uuid·이벤트명·timestamp·distinct_id가 모두 같으면 한 건으로 합친다
     */
    public void send(String event, String distinctId, Map<String, Object> properties, Instant timestamp, UUID uuid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("distinct_id", distinctId);
        payload.put("properties", properties);
        payload.put("timestamp", timestamp.toString());
        payload.put("uuid", uuid.toString());

        restClient.post()
                .uri("/batch/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("api_key", apiKey, "batch", List.of(payload)))
                .retrieve()
                .toBodilessEntity();
    }
}
