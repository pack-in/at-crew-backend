package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.billing.BillingService;
import com.atcrew.member.AccountDeletionFailedEvent;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.member.ProfileSectionSavedEvent;
import com.atcrew.portfolio.PortfolioService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
                eq("account_deleted:member-001"), isNull());
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
                eq("account_deleted:member-002"), isNull());
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

        verify(analyticsEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void 탈퇴_실패_이벤트는_에러코드를_실어_발행한다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.on(new AccountDeletionFailedEvent("member-004", "MEMBER_NOT_FOUND", occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        // 회원이 특정되는 실패라 publish로 보낸다 — account_type·plan_type이 같은 회원의 다른 이벤트와 맞는다.
        verify(analyticsEventPublisher).publish(eq("account_deletion_failed"), eq("member-004"),
                properties.capture(), eq("account_deletion_failed:member-004:" + occurredAt.toEpochMilli()),
                eq(occurredAt));
        assertThat(properties.getValue()).containsEntry("error_code", "MEMBER_NOT_FOUND");
    }

    @Test
    void 프로필_저장_이벤트는_완성도_변경필드수_최초완성여부를_실어_발행한다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.on(new ProfileSectionSavedEvent("member-005", "artist_info", 1.0, true, 5, true, occurredAt));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> properties = ArgumentCaptor.forClass(Map.class);
        verify(analyticsEventPublisher).publish(eq("profile_section_saved"), eq("member-005"),
                properties.capture(),
                eq("profile_section_saved:member-005:artist_info:" + occurredAt.toEpochMilli()), eq(occurredAt));
        assertThat(properties.getValue())
                .containsEntry("section_type", "artist_info")
                .containsEntry("completion_rate", 1.0)
                .containsEntry("is_complete", true)
                .containsEntry("changed_field_count", 5)
                .containsEntry("is_first_completion", true);
    }

    // E2E 2026-10-07에서 실제로 잡힌 결함 — 기본정보/구직정보가 한 저장 요청에서 같이 바뀌면 두
    // 이벤트의 occurredAt이 같아서, sectionType을 insertId에 안 넣으면 uuid가 같아져 PostHog가 하나로
    // 합친다. 두 섹션 이벤트의 insertId가 서로 달라야 한다.
    @Test
    void 같은_시각에_발행된_서로_다른_섹션_이벤트는_insertId가_다르다() {
        Instant occurredAt = Instant.parse("2026-10-05T00:00:00Z");

        listener.on(new ProfileSectionSavedEvent("member-006", "artist_info", 0.2, false, 1, false, occurredAt));
        listener.on(new ProfileSectionSavedEvent("member-006", "job_info", 0.0, false, 1, false, occurredAt));

        ArgumentCaptor<String> insertIds = ArgumentCaptor.forClass(String.class);
        verify(analyticsEventPublisher, org.mockito.Mockito.times(2))
                .publish(eq("profile_section_saved"), eq("member-006"), any(), insertIds.capture(), eq(occurredAt));
        assertThat(insertIds.getAllValues()).doesNotHaveDuplicates();
    }
}
