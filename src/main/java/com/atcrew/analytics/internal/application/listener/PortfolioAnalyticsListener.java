package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.portfolio.PortfolioArtworkAddedEvent;
import com.atcrew.portfolio.PortfolioArtworkRemovedEvent;
import com.atcrew.portfolio.PortfolioSubmitFailedEvent;
import com.atcrew.portfolio.PortfolioSubmitSucceededEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * portfolio 모듈 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고.
 */
@Component
class PortfolioAnalyticsListener {

    private static final String PORTFOLIO_ARTWORK_ADDED = "portfolio_artwork_added";
    private static final String PORTFOLIO_ARTWORK_REMOVED = "portfolio_artwork_removed";
    private static final String PORTFOLIO_SUBMIT_SUCCEEDED = "portfolio_submit_succeeded";
    private static final String PORTFOLIO_SUBMIT_FAILED = "portfolio_submit_failed";

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

    @ApplicationModuleListener
    void onSubmitSucceeded(PortfolioSubmitSucceededEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("portfolio_id", event.portfolioId());
        properties.put("mode", event.mode());
        properties.put("portfolio_order", event.portfolioOrder());
        properties.put("artwork_count", event.artworkCount());
        properties.put("kind", event.kind().name());
        properties.put("reflection_type", event.reflectionType() == null ? null : event.reflectionType().name());
        analyticsEventPublisher.publish(PORTFOLIO_SUBMIT_SUCCEEDED, event.memberId(), properties,
                AnalyticsInsertId.of(PORTFOLIO_SUBMIT_SUCCEEDED, event.portfolioId(), event.occurredAt()));
    }

    /**
     * 생성/수정 트랜잭션이 롤백되는 경로에서 발행되므로 레지스트리를 거치지 않는 동기 리스너로만 받는다
     * (package-info.java 참고) — 재시도는 없다. 막 롤백된 요청 스레드에서 공통 속성(plan_type/locale)
     * 조회로 DB를 또 왕복시키지 않도록 publishAnonymous를 쓴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    void onSubmitFailed(PortfolioSubmitFailedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", event.mode());
        properties.put("error_code", event.errorCode());
        analyticsEventPublisher.publishAnonymous(PORTFOLIO_SUBMIT_FAILED, event.memberId(), properties,
                AnalyticsInsertId.of(PORTFOLIO_SUBMIT_FAILED, event.memberId(), event.occurredAt()));
    }
}
