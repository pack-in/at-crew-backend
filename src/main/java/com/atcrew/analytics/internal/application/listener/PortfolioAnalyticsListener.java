package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.portfolio.PortfolioArtworkAddedEvent;
import com.atcrew.portfolio.PortfolioArtworkRemovedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * portfolio 모듈 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고. 이 두 이벤트는 전부 성공 계열이다.
 */
@Component
class PortfolioAnalyticsListener {

    private static final String PORTFOLIO_ARTWORK_ADDED = "portfolio_artwork_added";
    private static final String PORTFOLIO_ARTWORK_REMOVED = "portfolio_artwork_removed";

    private final AnalyticsEventPublisher analyticsEventPublisher;

    PortfolioAnalyticsListener(AnalyticsEventPublisher analyticsEventPublisher) {
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    @ApplicationModuleListener
    void onArtworkAdded(PortfolioArtworkAddedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("portfolio_id", event.portfolioId());
        properties.put("artwork_id", event.artworkId());
        properties.put("entry_source", event.entrySource());
        properties.put("kind", event.kind().name());
        properties.put("reflection_type", event.reflectionType() == null ? null : event.reflectionType().name());
        // 한 포트폴리오에 여러 작품이 같은 요청으로 추가될 수 있어 portfolioId만으로는 insertId가 겹친다.
        String insertId = AnalyticsInsertId.of(PORTFOLIO_ARTWORK_ADDED,
                event.portfolioId() + ":" + event.artworkId());
        analyticsEventPublisher.publish(PORTFOLIO_ARTWORK_ADDED, event.memberId(), properties, insertId);
    }

    @ApplicationModuleListener
    void onArtworkRemoved(PortfolioArtworkRemovedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("portfolio_id", event.portfolioId());
        properties.put("artwork_id", event.artworkId());
        properties.put("kind", event.kind().name());
        properties.put("reflection_type", event.reflectionType() == null ? null : event.reflectionType().name());
        String insertId = AnalyticsInsertId.of(PORTFOLIO_ARTWORK_REMOVED,
                event.portfolioId() + ":" + event.artworkId());
        analyticsEventPublisher.publish(PORTFOLIO_ARTWORK_REMOVED, event.memberId(), properties, insertId);
    }
}
