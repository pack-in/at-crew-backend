package com.atcrew.portfolio;

import java.time.Instant;

/**
 * 공유 링크로 열람하는 포트폴리오 헤더 정보 (docs/design/portfolio-module-design.md §4).
 *
 * <p>비로그인 응답이라 소유자 식별자(회원 ID·핸들)나 공유 슬러그는 담지 않는다.
 * 담긴 작품은 별도 목록 API(`/shared/{identifier}/artworks`)로 페이지 조회한다.
 */
public record PortfolioSharedInfo(
        String id,                     // 포트폴리오 ID
        PortfolioKind kind,            // 유형 — 작가 페이지 / 공유
        ReflectionType reflectionType, // 반영 유형 — 최신 반영형 / 고정형
        String title,                  // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)
        String ownerName,              // 헤더용 작성자 이름 — 고정형은 생성 시점에 얼린 이름, 그 외는 현재 이름
        int itemCount,                 // 담긴 작품 수
        Instant createdAt,             // 생성 시각
        Instant updatedAt              // 최종 수정 시각
) {
}
