package com.atcrew.portfolio.internal.application;

import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 포트폴리오 구성 정합성 보정 배치 (docs/design/portfolio-module-design.md §1.2, §5.5).
 *
 * <p>정합성의 1차 책임은 포트폴리오 트랜잭션 안의 동기 반영과 이벤트 수신이 지고, 이 배치는
 * 이벤트 유실·처리 실패로 남은 불일치를 회수하는 안전망이다 — 배치가 늦게 돌아도 열람 응답 자체는
 * 조회 시점에 원본을 다시 읽어 판정하므로 잘못된 작품이 노출되지 않는다.
 * fixedDelay라 시간대와 무관하며, 절대값 재계산이라 다중 인스턴스에서 중복 실행되어도 결과가 같다.
 */
@Component
class PortfolioMembershipReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortfolioMembershipReconcileScheduler.class);

    private final PortfolioItemRepository portfolioItemRepository;
    private final PortfolioMembershipReconciler reconciler;

    PortfolioMembershipReconcileScheduler(PortfolioItemRepository portfolioItemRepository,
                                          PortfolioMembershipReconciler reconciler) {
        this.portfolioItemRepository = portfolioItemRepository;
        this.reconciler = reconciler;
    }

    // 포트폴리오 단위로 트랜잭션을 나눈다 — 한 건이 실패해도 나머지 보정이 통째로 롤백되면 안 된다.
    @Scheduled(fixedDelay = 21_600_000)
    void reconcileMemberships() {
        List<String> portfolioIds = portfolioItemRepository.findDistinctPortfolioIds();
        int failed = 0;
        for (String portfolioId : portfolioIds) {
            try {
                reconciler.reconcilePortfolio(portfolioId);
            } catch (Exception e) {
                failed++;
                log.error("포트폴리오 정합성 보정 실패: portfolioId={}", portfolioId, e);
            }
        }
        if (!portfolioIds.isEmpty()) {
            log.info("포트폴리오 정합성 보정: portfolios={} failed={}", portfolioIds.size(), failed);
        }
    }
}
