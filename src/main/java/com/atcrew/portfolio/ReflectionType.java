package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        원본 작품 반영 유형 — 생성 시 결정되며 이후 전환할 수 없다.
        - LIVE: 최신 반영형. 원본 작품을 참조하므로 제목·썸네일 수정, 삭제, 공개범위 변경이 그대로 반영된다. \
        수정·작품 추가/제거가 가능하다. 작가 페이지는 항상 LIVE다.
        - SNAPSHOT: 고정형. 생성 시점의 표시 정보(작품 제목·썸네일·작성자 이름)를 복사해 원본 변경에 \
        영향받지 않는다. 생성 후에는 제목 변경·작품 추가/제거가 모두 409(SNAPSHOT_PORTFOLIO_IMMUTABLE)로 거부된다.""",
        example = "LIVE")
public enum ReflectionType {
    LIVE,     // 최신 반영형 — 원본 작품을 참조해 수정·삭제·공개범위 변경이 그대로 반영된다
    SNAPSHOT  // 고정형 — 생성 시점 표시 필드를 복사해 원본 변경에 영향받지 않는다
}
