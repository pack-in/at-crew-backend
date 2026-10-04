package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.billing.BillingService;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 이벤트를 account_deleted 분석 이벤트로 옮기는 리스너 단위 테스트(PA-02).
 */
class MemberAnalyticsListenerTest {

    AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);
    BillingService billingService = mock(BillingService.class);
    ArtworkService artworkService = mock(ArtworkService.class);
    PortfolioService portfolioService = mock(PortfolioService.class);

    MemberAnalyticsListener listener =
            new MemberAnalyticsListener(analyticsEventPublisher, billingService, artworkService, portfolioService);

    @Test
    void 탈퇴하면_구독_보유_여부와_보유_자산_수를_실어_발행한다() {
        when(billingService.hasProPlan("member-001")).thenReturn(true);
        when(artworkService.countActiveByAuthor("member-001")).thenReturn(7L);
        when(portfolioService.countOwnedByMember("member-001")).thenReturn(2);

        listener.onMemberDeactivated(new MemberDeactivatedEvent("member-001"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("account_deleted"), eq("member-001"), properties.capture(),
                eq("account_deleted:member-001"));
        assertThat(properties.getValue())
                .containsEntry("had_active_subscription", true)
                .containsEntry("artwork_count", 7L)
                .containsEntry("portfolio_count", 2);
    }

    @Test
    void 구독이_없고_보유_자산이_없는_회원도_발행한다() {
        when(billingService.hasProPlan("member-002")).thenReturn(false);
        when(artworkService.countActiveByAuthor("member-002")).thenReturn(0L);
        when(portfolioService.countOwnedByMember("member-002")).thenReturn(0);

        listener.onMemberDeactivated(new MemberDeactivatedEvent("member-002"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("account_deleted"), eq("member-002"), properties.capture(),
                eq("account_deleted:member-002"));
        assertThat(properties.getValue())
                .containsEntry("had_active_subscription", false)
                .containsEntry("artwork_count", 0L)
                .containsEntry("portfolio_count", 0);
    }

    // 속성 조회가 깨지면 이벤트를 반쪽으로 보내지 않고 예외로 끝낸다 — Modulith 레지스트리에 미완료로 남아
    // 재배포 때 다시 발행된다.
    @Test
    void 속성_조회가_실패하면_발행하지_않고_예외를_전파한다() {
        when(billingService.hasProPlan("member-003")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> listener.onMemberDeactivated(new MemberDeactivatedEvent("member-003")))
                .isInstanceOf(RuntimeException.class);

        verify(analyticsEventPublisher, never()).publish(any(), any(), any(), any());
    }
}
