package com.atcrew.portfolio;

/**
 * 포트폴리오에 작품이 새로 편입됐을 때 발행된다. 이미 포함돼 있던 작품의 재선언(순서 변경 등)은
 * 포함하지 않는다 — analytics가 PostHog {@code portfolio_artwork_added} 이벤트로 그대로 전달한다.
 *
 * @param entrySource 추가 경로 구분값. 지금은 호출 지점이 하나뿐이라 고정값을 쓴다.
 */
public record PortfolioArtworkAddedEvent(String portfolioId, String artworkId, String memberId,
                                          PortfolioKind kind, ReflectionType reflectionType,
                                          String entrySource) {
}
