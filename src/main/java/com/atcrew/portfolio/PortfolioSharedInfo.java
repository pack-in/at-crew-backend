package com.atcrew.portfolio;

import java.time.Instant;

/**
 * 공유 링크로 열람하는 포트폴리오 헤더 정보 (docs/design/portfolio-module-design.md §4).
 *
 * <p>비로그인 응답이라 소유자 회원 ID나 공유 슬러그는 담지 않는다. 다만 공개 URL 조립용으로 소유자의 현재 handle과
 * 공개 코드는 담는다 — FE가 요청 URL과 비교해 새 주소로 리다이렉트한다(member-handle-design §2.2).
 * 담긴 작품은 별도 목록 API(`/shared/{identifier}/artworks`)로 페이지 조회한다.
 */
public record PortfolioSharedInfo(
        String id,                     // 포트폴리오 ID
        PortfolioKind kind,            // 유형 — 작가 페이지 / 공유
        ReflectionType reflectionType, // 반영 유형 — 최신 반영형 / 고정형
        String title,                  // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)
        String ownerName,              // 헤더용 작성자 이름 — 고정형은 생성 시점에 얼린 이름, 그 외는 현재 이름
        String ownerHandle,            // 소유자의 현재 handle — 고정형도 현재 값(URL 조립용이라 얼리지 않는다)
        String publicCode,             // 공유 URL /{ownerHandle}/portfolio/{publicCode}의 6자 코드 — 작가 페이지는 null
        int itemCount,                 // 담긴 작품 수
        Instant createdAt,             // 생성 시각
        Instant updatedAt,             // 최종 수정 시각
        // 공유 미리보기(OG) 이미지 key — 담긴 작품 중 외부 열람 가능한 업로드 최신 1개(성인물은 블러본). 없으면 null(기본 이미지)
        String ogImageKey
) {
}
