package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        내 포트폴리오 목록 정렬 기준 — 커서(cursor)의 의미도 함께 바뀐다.
        - OLDEST: 오래된순. 생성일(createdAt) 오름차순, 커서는 createdAt epochMilli.
        - LATEST: 최신순. 생성일(createdAt) 내림차순, 커서는 createdAt epochMilli. 미지정 시 기본값.
        - UPDATED: 최근 수정순. 수정일(updatedAt) 내림차순, 커서는 updatedAt epochMilli.""",
        example = "LATEST")
public enum PortfolioSort {
    OLDEST,   // 오래된순 — 생성일 오름차순
    LATEST,   // 최신순 — 생성일 내림차순(기본값)
    UPDATED   // 최근 수정순 — 수정일 내림차순
}
