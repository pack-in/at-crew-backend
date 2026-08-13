package com.atcrew.billing;

import com.atcrew.SharedContainersConfig;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.exception.BillingException;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * billing 모듈 통합 검증 (docs/design/billing-module-design.md §1, §2.1).
 *
 * <p>Stripe Checkout·웹훅은 이번 단계 범위 밖이라 플랜 승급은 리포지토리로 구독 행을 직접 만들어
 * 시뮬레이션한다.
 */
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ExtendWith(DatabaseCleanupExtension.class)
@ImportTestcontainers(SharedContainersConfig.class)
class BillingModuleTests {

    @Autowired
    PlanService planService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Test
    void 구독_레코드가_없으면_스타터_기본값을_반환한다() {
        String memberId = newMemberId();

        PlanInfo plan = planService.getPlan(memberId);

        assertThat(plan.plan()).isEqualTo(Plan.STARTER);
        assertThat(plan.status()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(plan.currentPeriodEnd()).isNull();
        assertThat(plan.cancelAtPeriodEnd()).isFalse();
        assertThat(plan.pendingPlan()).isNull();
        assertThat(planService.isPro(memberId)).isFalse();
        assertThat(planService.artworkLimit(memberId)).isEqualTo(4);
    }

    @Test
    void 활성_프로_구독이면_프로로_판정하고_작품_상한이_해제된다() {
        String memberId = givenSubscription(Plan.PRO_MONTHLY, SubscriptionStatus.ACTIVE);

        assertThat(planService.getPlan(memberId).plan()).isEqualTo(Plan.PRO_MONTHLY);
        assertThat(planService.isPro(memberId)).isTrue();
        assertThat(planService.artworkLimit(memberId)).isEqualTo(Integer.MAX_VALUE);
        assertThatCode(() -> planService.assertPro(memberId)).doesNotThrowAnyException();
    }

    @Test
    void 체험_기간도_프로로_판정한다() {
        String memberId = givenSubscription(Plan.PRO_YEARLY, SubscriptionStatus.TRIALING);

        assertThat(planService.isPro(memberId)).isTrue();
    }

    // 결제 실패 시 게이팅은 스타터로 떨어지지만 plan은 유지돼 "프로 월간, 결제 실패" 표기가 가능해야 한다(§2.1).
    @Test
    void 결제_실패_구독은_플랜_표기를_유지한_채_게이팅만_스타터로_떨어진다() {
        String memberId = givenSubscription(Plan.PRO_MONTHLY, SubscriptionStatus.PAST_DUE);

        PlanInfo plan = planService.getPlan(memberId);

        assertThat(plan.plan()).isEqualTo(Plan.PRO_MONTHLY);
        assertThat(plan.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(planService.isPro(memberId)).isFalse();
        assertThat(planService.artworkLimit(memberId)).isEqualTo(4);
    }

    @Test
    void 프로가_아니면_assertPro가_예외를_던진다() {
        String memberId = givenSubscription(Plan.STARTER, SubscriptionStatus.NONE);

        assertThatThrownBy(() -> planService.assertPro(memberId))
                .isInstanceOf(BillingException.class)
                .hasMessageContaining("프로 플랜");
    }

    private String givenSubscription(Plan plan, SubscriptionStatus status) {
        String memberId = newMemberId();
        subscriptionRepository.save(Subscription.create(memberId, plan, status));
        return memberId;
    }

    // billing은 member 모듈을 참조하지 않는다(§1 단방향) — 회원 식별자는 값으로만 다룬다.
    private String newMemberId() {
        return UUID.randomUUID().toString();
    }
}
