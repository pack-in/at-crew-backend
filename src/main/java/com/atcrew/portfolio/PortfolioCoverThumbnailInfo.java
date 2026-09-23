package com.atcrew.portfolio;

/**
 * 포트폴리오 카드 커버 한 칸의 썸네일 (마이페이지_작가-R39).
 *
 * <p>목록 화면에서 포트폴리오 여러 건 × 최대 4장이 함께 내려가므로 카드 정보
 * ({@link PortfolioArtworkCardInfo})를 재사용하지 않고 썸네일 키만 담는 별도 타입으로 둔다.
 */
public record PortfolioCoverThumbnailInfo(
        String thumbKey,      // 커버 썸네일 R2 키 — 이미지 처리 전이면 null
        String thumbAdultKey  // 성인 블러 썸네일 R2 키 — 썸네일 변환 전이거나 변환 이전에 올라온 작품이면 null
) {
}
