package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.billing.BillingService;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.portfolio.PortfolioService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * member 모듈 이벤트를 분석 이벤트로 옮긴다(plans/261005-posthog-analytics-events).
 *
 * <p>패턴(성공/실패 리스너 구분, $insert_id)은 package-info.java 참고.
 */
@Component
class MemberAnalyticsListener {

    private static final String ACCOUNT_DELETED = "account_deleted";

    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final BillingService billingService;
    private final ArtworkService artworkService;
    private final PortfolioService portfolioService;

    MemberAnalyticsListener(AnalyticsEventPublisher analyticsEventPublisher,
                            BillingService billingService,
                            ArtworkService artworkService,
                            PortfolioService portfolioService) {
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.billingService = billingService;
        this.artworkService = artworkService;
        this.portfolioService = portfolioService;
    }

    /**
     * 탈퇴 집계 — 탈퇴 시점의 구독 보유 여부와 보유 자산 수를 함께 싣는다.
     *
     * <p>커밋 후에 실행되므로 회원은 이미 비활성화 상태다. 세 속성 모두 memberId만으로 세는 값이고
     * 탈퇴 처리(작품 강제 비공개·포트폴리오 공유 차단)는 개수를 줄이지 않으므로 커밋 후에 세도 값이 같다.
     *
     * <p>조회가 실패하면 예외를 그대로 던진다 — 이벤트가 미완료로 남아 재배포 때 다시 발행된다.
     */
    @ApplicationModuleListener
    void onMemberDeactivated(MemberDeactivatedEvent event) {
        String memberId = event.memberId();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("had_active_subscription", billingService.hasProPlan(memberId));
        properties.put("artwork_count", artworkService.countActiveByAuthor(memberId));
        properties.put("portfolio_count", portfolioService.countOwnedByMember(memberId));
        analyticsEventPublisher.publish(ACCOUNT_DELETED, memberId, properties,
                AnalyticsInsertId.of(ACCOUNT_DELETED, memberId));
    }
}
