package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.portfolio.PortfolioArtworkAddedEvent;
import com.atcrew.portfolio.PortfolioArtworkRemovedEvent;
import com.atcrew.portfolio.PortfolioKind;
import com.atcrew.portfolio.ReflectionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * portfolio 작품 편입/제외 이벤트를 분석 이벤트로 옮기는 리스너 단위 테스트(PA-06).
 */
class PortfolioAnalyticsListenerTest {

    AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);

    PortfolioAnalyticsListener listener = new PortfolioAnalyticsListener(analyticsEventPublisher);

    @Test
    void 작품이_추가되면_포트폴리오와_작품ID로_구분되는_insertId로_발행한다() {
        listener.onArtworkAdded(new PortfolioArtworkAddedEvent(
                "portfolio-1", "artwork-1", "member-1", PortfolioKind.SHARED, ReflectionType.LIVE, "editor"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("portfolio_artwork_added"), eq("member-1"),
                properties.capture(), eq("portfolio_artwork_added:portfolio-1:artwork-1"), isNull());
        assertThat(properties.getValue())
                .containsEntry("portfolio_id", "portfolio-1")
                .containsEntry("artwork_id", "artwork-1")
                .containsEntry("entry_source", "editor")
                .containsEntry("kind", "SHARED")
                .containsEntry("reflection_type", "LIVE");
    }

    @Test
    void 작품이_제외되면_제외_이벤트로_발행한다() {
        listener.onArtworkRemoved(new PortfolioArtworkRemovedEvent(
                "portfolio-1", "artwork-2", "member-1", PortfolioKind.SHARED, ReflectionType.SNAPSHOT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("portfolio_artwork_removed"), eq("member-1"),
                properties.capture(), eq("portfolio_artwork_removed:portfolio-1:artwork-2"), isNull());
        assertThat(properties.getValue())
                .containsEntry("portfolio_id", "portfolio-1")
                .containsEntry("artwork_id", "artwork-2")
                .containsEntry("kind", "SHARED")
                .containsEntry("reflection_type", "SNAPSHOT");
    }
}
