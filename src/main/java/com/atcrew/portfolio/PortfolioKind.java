package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        포트폴리오 유형 — 생성 방식과 게이팅이 다르다.
        - ARTIST_PAGE: 작가 페이지 포트폴리오. 회원당 1개이며 `GET /api/portfolios/me` 또는 \
        `GET /api/portfolios/selectable` 최초 호출 시 자동 생성된다. 전 플랜 제공이고 제목(title)과 \
        공유 슬러그(shareSlug)가 없으며(null), 공개 열람은 작가 handle로 한다. 삭제할 수 없다.
        - SHARED: 공유 포트폴리오. `POST /api/portfolios`로 만들며 프로 플랜 전용이다. 제목이 필수이고 \
        생성 시 공유 슬러그가 발급된다.""",
        example = "SHARED")
public enum PortfolioKind {
    ARTIST_PAGE,  // 작가 페이지 포트폴리오 — 회원당 1개, 전 플랜 제공, 제목 없이 사용자 이름 헤더를 쓴다
    SHARED        // 공유 포트폴리오 — 프로 전용, 공유 슬러그로 링크를 발급한다
}
