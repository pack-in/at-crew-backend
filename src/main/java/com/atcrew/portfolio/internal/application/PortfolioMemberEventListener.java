package com.atcrew.portfolio.internal.application;

import com.atcrew.member.MemberDeactivatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
class PortfolioMemberEventListener {

    private final PortfolioBlocker portfolioBlocker;

    PortfolioMemberEventListener(PortfolioBlocker portfolioBlocker) {
        this.portfolioBlocker = portfolioBlocker;
    }

    // 탈퇴하면 소유 포트폴리오의 공유 링크 열람을 막는다(§5.2).
    @ApplicationModuleListener
    void on(MemberDeactivatedEvent event) {
        portfolioBlocker.blockOwnedPortfolios(event.memberId());
    }
}
