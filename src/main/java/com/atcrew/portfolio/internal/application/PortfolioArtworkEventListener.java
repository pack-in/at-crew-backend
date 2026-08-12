package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.ArtworkPortfolioSelectionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * artwork 모듈이 발행하는 기존 이벤트를 받아 포트폴리오 구성을 정리한다
 * (docs/design/portfolio-module-design.md §1.2 — 신규 이벤트를 만들지 않고 기존 이벤트를 재사용한다).
 *
 * <p>{@code @ApplicationModuleListener}(비동기 + 커밋 이후 + REQUIRES_NEW)를 쓰는 이유는 원본 트랜잭션
 * 커밋 이후에 재조회해야 stale 상태를 읽지 않기 때문이다({@code ArtworkSearchIndexer}와 동일한 판단).
 * 처리가 실패하면 미완료 이벤트로 남고, 그래도 어긋난 값은 6시간 주기 보정이 회수한다(§5.5).
 */
@Component
class PortfolioArtworkEventListener {

    private final PortfolioMembershipReconciler reconciler;
    private final PortfolioServiceImpl portfolioService;

    PortfolioArtworkEventListener(PortfolioMembershipReconciler reconciler,
                                  PortfolioServiceImpl portfolioService) {
        this.reconciler = reconciler;
        this.portfolioService = portfolioService;
    }

    /**
     * 업로드·노출 위치 재선언에 따른 편입 반영 (업로드-R09).
     *
     * <p>위 두 리스너와 달리 {@code @EventListener}(동기 + 발행 트랜잭션 안)를 쓴다 — 소유·유형·플랜 검증에
     * 실패하면 예외를 그대로 전파해 작품 저장까지 롤백해야 하기 때문이다. 비동기로 처리하면 작품은 저장됐는데
     * 편입은 안 된 반쪽 상태가 남고, 스타터가 공유 포트폴리오를 지정한 경우 사용자에게 알릴 방법도 없다.
     */
    @EventListener
    void onPortfolioSelectionRequested(ArtworkPortfolioSelectionRequested event) {
        portfolioService.applyPortfolioSelection(event.memberId(), event.artworkId(), event.portfolioIds());
    }

    // 작품의 모든 변경에 발행되는 범용 이벤트라 여기서 종류를 가리지 않고, 재계산 쪽이 상태(휴지통 이동·복원)
    // 기준으로만 판정한다 — 비공개 전환·수정만 있었다면 결과가 같아 아무것도 바뀌지 않는다(마이페이지_작가-R38).
    @ApplicationModuleListener
    void onArtworkChanged(ArtworkChangedEvent event) {
        reconciler.reconcileArtwork(event.artworkId());
    }

    // 영구 삭제 — 원본이 완전히 사라졌으므로 구성 행을 제거한다.
    @ApplicationModuleListener
    void onArtworkPermanentlyDeleted(ArtworkPermanentlyDeletedEvent event) {
        reconciler.removeArtwork(event.artworkId());
    }
}
