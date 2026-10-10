package com.atcrew.portfolio;

/**
 * 포트폴리오에서 작품이 제외됐을 때 발행된다 — analytics가 PostHog
 * {@code portfolio_artwork_removed} 이벤트로 그대로 전달한다.
 */
public record PortfolioArtworkRemovedEvent(String portfolioId, String artworkId, String memberId,
                                            PortfolioKind kind, ReflectionType reflectionType) {
}
