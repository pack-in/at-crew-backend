package com.atcrew.portfolio;

import java.time.Instant;
import java.util.List;

/**
 * 내 포트폴리오 목록의 카드 1건 (docs/design/portfolio-module-design.md §4).
 */
public record PortfolioSummaryInfo(
        String id,                                          // 포트폴리오 ID
        PortfolioKind kind,                                 // 유형 — 작가 페이지 / 공유
        ReflectionType reflectionType,                      // 반영 유형 — 최신 반영형 / 고정형
        String title,                                       // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)
        String shareSlug,                                   // 공유 링크 슬러그 — 공유 포트폴리오만, 작가 페이지는 null
        int itemCount,                                      // 담긴 작품 수 — 카드의 "N개" 표기용
        List<PortfolioCoverThumbnailInfo> coverThumbnails,  // 카드 커버 2x2 썸네일 — 업로드 오래된순 최대 4개
        Instant createdAt,                                  // 생성 시각
        Instant updatedAt,                                  // 최종 변경 시각 — 시스템 변경(구성 재계산 등) 포함
        Instant lastEditedAt                                // [수정하기]로 저장한 시각 — "업데이트순" 정렬 기준
) {
}
