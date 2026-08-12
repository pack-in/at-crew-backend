package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품 상세에서 이미지 여러 장을 보여주는 방식.
        - VERTICAL_SCROLL: 세로로 이어 붙여 스크롤 (웹툰형).
        - HORIZONTAL_SWIPE: 가로로 넘겨 보기 (갤러리형).""",
        example = "VERTICAL_SCROLL")
public enum ImageLayoutType {
    VERTICAL_SCROLL, HORIZONTAL_SWIPE
}
