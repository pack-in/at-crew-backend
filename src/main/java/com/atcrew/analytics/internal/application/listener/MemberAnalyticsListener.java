package com.atcrew.analytics.internal.application.listener;

import com.atcrew.analytics.AnalyticsEventPublisher;
import com.atcrew.analytics.internal.application.AnalyticsInsertId;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.billing.BillingService;
import com.atcrew.member.AccountDeletionFailedEvent;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.member.ProfileSectionSavedEvent;
import com.atcrew.portfolio.PortfolioService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    private static final String ACCOUNT_DELETION_FAILED = "account_deletion_failed";
    private static final String PROFILE_SECTION_SAVED = "profile_section_saved";

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
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void onMemberDeactivated(MemberDeactivatedEvent event) {
        String memberId = event.memberId();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("had_active_subscription", billingService.hasProPlan(memberId));
        properties.put("artwork_count", artworkService.countActiveByAuthor(memberId));
        properties.put("portfolio_count", portfolioService.countOwnedByMember(memberId));
        analyticsEventPublisher.publish(ACCOUNT_DELETED, memberId, properties,
                AnalyticsInsertId.of(ACCOUNT_DELETED, memberId), null);
    }

    /**
     * 탈퇴 처리 실패 집계 — 실패 원인 에러 코드만 싣는다.
     *
     * <p>롤백된 트랜잭션에는 레지스트리에 완료 표시를 남길 커밋이 없어 {@code AFTER_ROLLBACK}로 받고,
     * 실패 응답을 늦추지 않도록 별도 스레드에서 보낸다(package-info 참고). 재시도가 없으므로 전송 유실을 감수한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    void on(AccountDeletionFailedEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("error_code", event.errorCode());
        analyticsEventPublisher.publish(ACCOUNT_DELETION_FAILED, event.memberId(), properties,
                AnalyticsInsertId.of(ACCOUNT_DELETION_FAILED, event.memberId(), event.occurredAt()),
                event.occurredAt());
    }

    /**
     * 프로필 정보 저장 집계 — 완성도·변경 필드 수·최초 완성 여부를 함께 싣는다.
     *
     * <p>같은 회원이 반복해서 저장할 수 있는 이벤트라 {@code $insert_id}에 발생 시각까지 넣는다.
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(ProfileSectionSavedEvent event) {
        String memberId = event.memberId();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("section_type", event.sectionType());
        properties.put("completion_rate", event.completionRate());
        properties.put("is_complete", event.isComplete());
        properties.put("changed_field_count", event.changedFieldCount());
        properties.put("is_first_completion", event.isFirstCompletion());
        // 기본정보/구직정보가 한 저장 요청에서 같이 바뀌면 같은 occurredAt으로 이벤트가 2개 난다
        // (MemberServiceImpl.updateInfo) — sectionType을 insertId에 안 넣으면 두 이벤트의 uuid가 같아져
        // PostHog가 하나로 합친다(E2E 2026-10-07에 $insert_id 충돌로 발견, uuid도 insertId로 만든다).
        analyticsEventPublisher.publish(PROFILE_SECTION_SAVED, memberId, properties,
                AnalyticsInsertId.of(PROFILE_SECTION_SAVED, memberId + ":" + event.sectionType(),
                        event.occurredAt()), event.occurredAt());
    }
}
