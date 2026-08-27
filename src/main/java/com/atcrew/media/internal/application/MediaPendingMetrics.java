package com.atcrew.media.internal.application;

import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.internal.persistence.MediaAssetRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 처리되지 않은 채 남아 있는 이미지 수를 지표로 노출한다(docs/design/observability-design.md §6).
 *
 * <p>{@code atcrew_media_processing_total}은 <b>콜백이 도착해야</b> 증가한다. 그래서 Worker의
 * 콜백 URL이 잘못돼 결과가 서버로 아예 돌아오지 않는 유형은 그 카운터로 잡히지 않는다 — 실제로
 * {@code SERVER_CALLBACK_URL}이 임시 터널을 가리킨 채 2026-08-18~08-27 동안 콜백이 한 건도
 * 도착하지 않았는데 아무 알람도 울리지 않았다(이슈 #59).
 *
 * <p>"오지 않는 것"은 카운터가 아니라 잔량으로만 드러난다. 업로드된 지 오래됐는데 여전히
 * PENDING인 자산 수를 재서, 재시도로도 복구되지 않는 상태를 알람이 볼 수 있게 한다.
 *
 * <p>임계값은 {@link ImageRetryScheduler}의 재시도 주기(5분)와 재시도 대상 판정(10분)보다 넉넉히
 * 잡는다 — 정상적으로 재시도 중인 자산이 알람으로 새지 않아야 한다.
 */
@Component
class MediaPendingMetrics {

    private static final Logger log = LoggerFactory.getLogger(MediaPendingMetrics.class);

    /** 이 시간을 넘겨 PENDING이면 재시도로 복구되는 정상 흐름이 아니라고 본다. */
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(15);

    private final MediaAssetRepository assets;
    private final AtomicLong pendingCount = new AtomicLong();

    MediaPendingMetrics(MediaAssetRepository assets, MeterRegistry registry) {
        this.assets = assets;
        Gauge.builder("atcrew.media.pending.assets", pendingCount, AtomicLong::doubleValue)
                .description("업로드 후 15분이 지나도 처리되지 않은 이미지 수 — 콜백이 돌아오지 않는 상태를 나타낸다")
                .register(registry);
    }

    @Scheduled(fixedDelay = 60_000)
    void refresh() {
        try {
            pendingCount.set(assets.countByProcessingStatusAndCreatedAtBefore(
                    MediaProcessingStatus.PENDING, Instant.now().minus(STUCK_THRESHOLD)));
        } catch (Exception e) {
            // 지표 수집 실패가 애플리케이션에 영향을 주면 안 된다. 값은 직전 것을 유지한다.
            log.warn("미처리 이미지 수 조회 실패", e);
        }
    }
}
