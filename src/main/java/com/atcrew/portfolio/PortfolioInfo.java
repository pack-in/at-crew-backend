package com.atcrew.portfolio;

import java.time.Instant;
import java.util.List;

/**
 * 포트폴리오 상세 (docs/design/portfolio-module-design.md §4).
 */
public record PortfolioInfo(
        String id,                                // 포트폴리오 ID
        PortfolioKind kind,                       // 유형 — 작가 페이지 / 공유
        ReflectionType reflectionType,            // 반영 유형 — 최신 반영형 / 고정형
        String title,                             // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)
        String shareSlug,                         // 공유 링크 슬러그 — 공유 포트폴리오만, 작가 페이지는 null
        int itemCount,                            // 담긴 작품 수
        List<PortfolioArtworkCardInfo> artworks,  // 담긴 작품 목록 — 업로드순(오래된순) 고정
        Instant createdAt,                        // 생성 시각
        Instant updatedAt                         // 최종 수정 시각
) {
}
