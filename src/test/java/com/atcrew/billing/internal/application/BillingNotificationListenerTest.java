package com.atcrew.billing.internal.application;

import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.common.mail.MailSender;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 정기 결제 실패 메일(설정-R03, QA-041)의 수신자·본문을 고정한다. */
class BillingNotificationListenerTest {

    private static final String MEMBER_ID = "member-1";
    private static final String EMAIL = "artist@example.com";

    private MemberService memberService;
    private MailSender mailSender;
    private BillingNotificationListener listener;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        mailSender = mock(MailSender.class);
        listener = new BillingNotificationListener(memberService, mailSender,
                new BillingProperties("https://at-crew.com", 0, Map.of()));
    }

    @Test
    void 결제_실패_메일은_회원_이메일로_플랜과_요금제_화면_링크를_담아_보낸다() {
        givenActiveMember("<작가>");

        listener.onPaymentFailed(new SubscriptionPaymentFailedEvent(MEMBER_ID, PlanType.PRO_YEARLY, Instant.now(), 1L));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq(EMAIL), eq("정기 결제에 실패했어요"), html.capture());
        assertThat(html.getValue())
                .contains("프로 플랜 연간")
                .contains("https://at-crew.com/setting?tab=billing")
                // 이름은 회원이 정한 값이라 HTML로 해석되지 않게 이스케이프한다.
                .contains("&lt;작가&gt;")
                .doesNotContain("<작가>");
    }

    @Test
    void 월간_플랜이면_월간으로_표기한다() {
        givenActiveMember("작가");

        listener.onPaymentFailed(new SubscriptionPaymentFailedEvent(MEMBER_ID, PlanType.PRO_MONTHLY, Instant.now(), 1L));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(anyString(), anyString(), html.capture());
        assertThat(html.getValue()).contains("프로 플랜 월간");
    }

    @Test
    void 탈퇴한_회원에게는_보내지_않고_예외도_던지지_않는다() {
        when(memberService.findAllByIds(List.of(MEMBER_ID))).thenReturn(Map.of());

        listener.onPaymentFailed(new SubscriptionPaymentFailedEvent(MEMBER_ID, PlanType.PRO_MONTHLY, Instant.now(), 1L));

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void Stripe_재시도_실패에는_다시_보내지_않는다() {
        givenActiveMember("작가");

        listener.onPaymentFailed(new SubscriptionPaymentFailedEvent(MEMBER_ID, PlanType.PRO_MONTHLY, Instant.now(), 2L));

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    private void givenActiveMember(String name) {
        MemberInfo member = mock(MemberInfo.class);
        when(member.loginEmail()).thenReturn(EMAIL);
        when(member.name()).thenReturn(name);
        when(memberService.findAllByIds(List.of(MEMBER_ID))).thenReturn(Map.of(MEMBER_ID, member));
    }
}
