package com.atcrew.portfolio.internal.application;

import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 탈퇴·운영 조치로 소유 포트폴리오의 공유 열람을 차단한다 (docs/design/portfolio-module-design.md §5.2).
 *
 * <p>탈퇴 이벤트 수신 경로와 공유 열람 조회 시점의 이중 확인 경로가 함께 쓴다.
 */
@Component
class PortfolioBlocker {

    private static final Logger log = LoggerFactory.getLogger(PortfolioBlocker.class);

    private final PortfolioRepository portfolioRepository;

    PortfolioBlocker(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * 해당 회원 소유의 포트폴리오 전부(공유 + 작가 페이지)를 차단한다. 이미 차단된 건은 최초 차단 시각을 유지한다.
     *
     * <p>별도 트랜잭션으로 커밋한다 — 공유 열람 조회에서 호출될 때는 호출자가 곧바로 410을 던져 그쪽
     * 트랜잭션이 롤백되므로, 같은 트랜잭션에 묶으면 차단 상태가 남지 않는다
     * ({@code LoginAttemptLimiter.recordFailure}와 같은 이유).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void blockOwnedPortfolios(String ownerMemberId) {
        List<Portfolio> portfolios = portfolioRepository.findByOwnerMemberId(ownerMemberId);
        // 변경 감지로 반영된다 — 별도 save() 호출은 중복이다.
        portfolios.forEach(Portfolio::block);
        log.info("포트폴리오 공유 열람 차단: memberId={} count={}", ownerMemberId, portfolios.size());
    }
}
