package com.atcrew.artwork;

/**
 * 작품의 색인 대상 상태(공개 여부·내용)가 바뀔 수 있는 모든 시점에 발행되는 이벤트.
 *
 * <p>색인/제거 판단은 수신 측(search 모듈)이 {@link ArtworkService#getArtworkForIndexing(String)}로
 * 재조회해서 내린다 — 페이로드를 얇게 유지해 artwork 모듈과의 결합을 최소화하고, 재조회 기반이라
 * 중복 발행·순서 뒤바뀜에도 안전(멱등)하다 (docs/design/search-module-design.md §5.1).
 */
public record ArtworkChangedEvent(String artworkId) {
}
