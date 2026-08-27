package com.atcrew.media.internal.application;

import com.atcrew.media.MediaAssetProcessedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 이미지 처리 결과를 센다(docs/design/observability-design.md §6).
 *
 * <p>Worker 콜백은 실패 상태로 들어와도 HTTP 204라 HTTP 지표로는 성공과 구분되지 않는다.
 * 사용자에게는 "업로드했는데 썸네일이 안 보이는" 형태로 나타나므로 별도로 센다.
 */
@Component
class MediaMetrics {

    private final MeterRegistry registry;

    MediaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    void onProcessed(MediaAssetProcessedEvent event) {
        Counter.builder("atcrew.media.processing")
                .description("이미지 후처리 콜백 처리 건수")
                .tag("status", event.status().name())
                .tag("owner_type", event.ownerType().name())
                .register(registry)
                .increment();
    }
}
