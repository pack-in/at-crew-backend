package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkService;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItem;
import com.atcrew.portfolio.internal.persistence.PortfolioItemRepository;
import com.atcrew.portfolio.internal.persistence.PortfolioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 포트폴리오 구성과 원본 작품 상태의 정합성 재계산 (docs/design/portfolio-module-design.md §1.2, §5.4, §5.5).
 *
 * <p>포트폴리오 API를 거치는 변경(추가·제거·교체·삭제)은 {@code PortfolioServiceImpl}이 같은 트랜잭션에서
 * 반영하지만, 작품 쪽에서 시작된 변경(휴지통 이동·복원·비공개 전환·영구 삭제)은 그 경로를 타지 않는다.
 * 그 몫을 이벤트 수신({@code PortfolioArtworkEventListener})과 6시간 주기 보정
 * ({@code PortfolioMembershipReconcileScheduler})이 이 컴포넌트를 공유해 처리한다.
 *
 * <p>재계산은 전부 절대값 기준이라 멱등하다 — 중복 이벤트나 순서 역전에도 최종 상태가 같다.
 *
 * <p><b>노출 제외 트리거는 세 가지뿐이다</b>(마이페이지_작가-R38·R39): (1) 원본의 휴지통 이동,
 * (2) 사용자가 포트폴리오에서 명시적으로 제외({@code PortfolioServiceImpl.removeArtwork}),
 * (3) 원본 영구 삭제. <b>작품 피드 공개 OFF(visibility 전환)는 제외 트리거가 아니다</b> —
 * 라이브 포트폴리오 소속 자체가 원본의 유효한 공개 위치라 비공개 작품도 그대로 노출되고,
 * 여기서 행을 지우면 편입 여부가 false로 떨어져 스스로 완전 비공개로 만들어버린다(§5.4,
 * {@code Artwork.accessFor}). 그래서 이 컴포넌트는 visibility를 아예 보지 않는다 —
 * 개수도 구성 행도 상태(휴지통·영구 삭제) 기준으로만 판정한다.
 */
@Component
class PortfolioMembershipReconciler {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final ArtworkService artworkService;

    PortfolioMembershipReconciler(PortfolioRepository portfolioRepository,
                                  PortfolioItemRepository portfolioItemRepository,
                                  ArtworkService artworkService) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioItemRepository = portfolioItemRepository;
        this.artworkService = artworkService;
    }

    /**
     * 원본 상태 변경(휴지통 이동·복원) 반영 — 구성 행은 그대로 두고 열람 가능 개수만 다시 센다.
     * 커버 썸네일은 목록 조회 시점에 원본을 읽어 판정하므로 저장할 것이 없다(§7).
     *
     * <p>비공개 전환·제목 수정처럼 상태가 그대로인 변경에서는 다시 센 값이 기존 값과 같아 아무것도 쓰지 않는다
     * (마이페이지_작가-R38 — 공개 OFF만으로는 포트폴리오 노출 목록에서 빠지지 않는다).
     */
    @Transactional
    void reconcileArtwork(String artworkId) {
        portfolioItemRepository.findPortfolioIdsByArtworkId(artworkId).forEach(this::recountItems);
    }

    /** 원본 영구 삭제 반영 — 되돌릴 원본이 없으므로 구성 행 자체를 지우고 개수를 다시 센다(§1.2). */
    @Transactional
    void removeArtwork(String artworkId) {
        List<String> portfolioIds = portfolioItemRepository.findPortfolioIdsByArtworkId(artworkId);
        if (portfolioIds.isEmpty()) {
            return;
        }
        // 남은 행의 ordinal은 다시 매기지 않는다 — 조회는 ordinal 오름차순·커서 비교만 쓰므로 빈 번호가 있어도
        // 무해하고, 재번호 매기기는 uk_pi_order 충돌 위험만 늘린다(구성 교체 경로가 이미 전체를 다시 매긴다).
        portfolioItemRepository.deleteByArtworkId(artworkId);
        portfolioIds.forEach(this::recountItems);
    }

    /**
     * 주기 보정 (§5.5) — 이벤트 유실로 남은 불일치를 포트폴리오 단위로 바로잡는다.
     * 이벤트 경로와 달리 (1) 원본이 영구 삭제된 고아 구성 행 제거와 (2) {@code artworks.portfolio_included}
     * 값 보정까지 함께 수행한다.
     *
     * <p>구성 행이 전혀 없는데 편입 여부만 true로 남은 작품은 보정 대상에서 빠진다 — portfolio에서 그런
     * 작품을 찾아낼 조회 수단이 없고(artwork 배치 조회 API 미도입, §8.3), 편입 해제는 항상 포트폴리오
     * 트랜잭션 안에서 동기 반영되므로 실제로 발생하지 않는 경로다.
     */
    @Transactional
    void reconcilePortfolio(String portfolioId) {
        Portfolio portfolio = livePortfolio(portfolioId);
        if (portfolio == null) {
            return;
        }
        int viewableCount = 0;
        for (PortfolioItem item : portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolioId)) {
            Optional<ArtworkInfo> artwork = artworkService.getArtworkForIndexing(item.getArtworkId());
            if (artwork.isEmpty()) {
                // 영구 삭제 이벤트를 놓친 고아 행 — 원본이 없으므로 편입 여부를 되돌릴 대상도 없다.
                portfolioItemRepository.delete(item);
                continue;
            }
            if (isViewable(artwork.get())) {
                viewableCount++;
            }
            syncInclusion(artwork.get());
        }
        applyItemCount(portfolio, viewableCount);
    }

    // 열람 가능 개수 = 원본이 남아 있고 휴지통·운영 차단 상태가 아닌 구성 행의 수(§2.1 itemCount).
    // 비공개 작품도 라이브 포트폴리오 소속 자체가 공개 위치라 그대로 노출되므로 개수에 포함한다
    // (마이페이지_작가-R39, §5.4).
    private void recountItems(String portfolioId) {
        Portfolio portfolio = livePortfolio(portfolioId);
        if (portfolio == null) {
            return;
        }
        int viewableCount = 0;
        for (PortfolioItem item : portfolioItemRepository.findByPortfolioIdOrderByOrdinal(portfolioId)) {
            if (isViewable(item.getArtworkId())) {
                viewableCount++;
            }
        }
        applyItemCount(portfolio, viewableCount);
    }

    // 고정형은 portfolio_items가 아니라 스냅샷 행으로 개수를 확정하므로 재계산 대상이 아니다(§1.2, §5.1).
    private Portfolio livePortfolio(String portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .filter(portfolio -> portfolio.getReflectionType() == ReflectionType.LIVE)
                .orElse(null);
    }

    private boolean isViewable(String artworkId) {
        return artworkService.getArtworkForIndexing(artworkId)
                .filter(this::isViewable)
                .isPresent();
    }

    // 운영 차단은 이벤트를 발행하지 않는 DB 직접 UPDATE로 이뤄지므로(docs/operations/moderation-block.md)
    // 개수 보정은 6시간 주기 배치가 회수한다(마이페이지_작가-R39).
    private boolean isViewable(ArtworkInfo artwork) {
        return artwork.status() != ArtworkStatus.DELETED && !artwork.blocked();
    }

    // 값이 같으면 쓰지 않는다 — 변경 감지로 매번 UPDATE가 나가면 낙관적 락 버전과 updated_at이 흔들린다.
    private void applyItemCount(Portfolio portfolio, int viewableCount) {
        if (portfolio.getItemCount() != viewableCount) {
            // 변경 감지로 반영된다 — 별도 save() 호출은 중복이다.
            portfolio.updateItemCount(viewableCount);
        }
    }

    // 편입 여부는 라이브 멤버십 절대값 기준이다(§5.4). 이미 맞는 값이면 쓰지 않는다 —
    // 6시간마다 모든 편입 작품의 updated_at을 흔들지 않기 위함이다.
    private void syncInclusion(ArtworkInfo artwork) {
        boolean included = portfolioItemRepository.countByArtworkId(artwork.id()) > 0;
        if (artwork.portfolioIncluded() != included) {
            artworkService.updatePortfolioInclusion(artwork.id(), included);
        }
    }
}
