package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 포트폴리오 카드 커버 한 칸의 썸네일 (마이페이지_작가-R39).
 *
 * <p>목록 화면에서 포트폴리오 여러 건 × 최대 4장이 함께 내려가므로 카드 정보
 * ({@link PortfolioArtworkCardInfo})를 재사용하지 않고 썸네일 키만 담는 별도 타입으로 둔다.
 */
@Schema(description = "포트폴리오 카드 커버 2x2 한 칸의 썸네일")
public record PortfolioCoverThumbnailInfo(
        @Schema(description = "커버 썸네일 R2 키 — 이미지 처리(Worker)가 끝나기 전이면 null",
                example = "thumb/019ff382-bd4a-7045-80ac-7430bd0832c7.avif", nullable = true)
        String thumbKey,      // 커버 썸네일 R2 키 — 이미지 처리 전이면 null

        @Schema(description = "성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰거나 이미지 처리 전이면 null",
                example = "thumb-adult/019ff382-bd4a-7045-80ac-7430bd0832c7.avif", nullable = true)
        String thumbAdultKey  // 성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰는 경우 null
) {
}
