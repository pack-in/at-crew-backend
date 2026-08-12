package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품 연령 등급 — 성인물은 목록에서 블러 썸네일(thumbAdultKey)을 사용한다.
        - ALL: 전체연령가.
        - R18: 성인물 (성적 콘텐츠).
        - G18: 성인물 (고어·폭력).""",
        example = "ALL")
public enum AgeRating {
    ALL,   // 전체연령가
    R18,   // R18 (성인물 — 성적 콘텐츠)
    G18    // G18 (성인물 — 고어/폭력)
}
