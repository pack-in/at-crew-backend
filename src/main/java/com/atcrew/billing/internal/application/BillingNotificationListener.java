package com.atcrew.billing.internal.application;

import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionPaymentFailedEvent;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.common.mail.MailSender;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * 정기 결제 실패를 회원에게 메일로 알린다(설정-R03, QA-041).
 *
 * <p>커밋 뒤에 발송한다 — 웹훅 처리가 롤백되면 실제로는 플랜이 바뀌지 않았는데 실패 메일만 나가게 된다.
 * 같은 실패 웹훅이 재전송돼도 상태가 이미 PAST_DUE면 이벤트 자체가 다시 나지 않으므로 메일도 한 번만 간다.
 */
@Component
class BillingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BillingNotificationListener.class);

    private final MemberService memberService;
    private final MailSender mailSender;
    private final BillingProperties properties;

    BillingNotificationListener(MemberService memberService, MailSender mailSender, BillingProperties properties) {
        this.memberService = memberService;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @ApplicationModuleListener
    void onPaymentFailed(SubscriptionPaymentFailedEvent event) {
        // 탈퇴 회원은 결과에서 빠진다. findById는 예외를 던져 이벤트가 끝없이 재발행 대상으로 남는다.
        MemberInfo member = memberService.findAllByIds(List.of(event.memberId())).get(event.memberId());
        if (member == null || member.loginEmail() == null) {
            log.info("결제 실패 메일 생략 — 활성 회원이 아님: memberId={}", event.memberId());
            return;
        }

        String settingsUrl = properties.frontendBaseUrl() + BillingServiceImpl.PORTAL_RETURN_PATH;
        String html = """
                <p>@ 정기 결제에 실패했어요</p>
                <p>%s님, %s 정기 결제가 처리되지 않았어요.<br/>
                결제가 완료되기 전까지는 스타터 플랜으로 이용하게 돼요.</p>
                <p>설정 &gt; 요금제 및 결제에서 결제 수단을 확인해주세요.<br/>
                결제가 완료되면 프로 플랜이 바로 복원돼요.</p>
                <p><a href="%s">요금제 및 결제 바로가기</a></p>
                <p>앳크루를 이용해주셔서 감사합니다.</p>
                """.formatted(HtmlUtils.htmlEscape(member.name()), planLabel(event.plan()), settingsUrl);
        mailSender.send(member.loginEmail(), "정기 결제에 실패했어요", html);
        log.info("결제 실패 메일 발송: memberId={}, plan={}", event.memberId(), event.plan());
    }

    // 설정-R03의 상태 표기('프로 플랜 월간, 결제 실패')와 같은 이름을 쓴다.
    private static String planLabel(PlanType plan) {
        return plan == PlanType.PRO_YEARLY ? "프로 플랜 연간" : "프로 플랜 월간";
    }
}
