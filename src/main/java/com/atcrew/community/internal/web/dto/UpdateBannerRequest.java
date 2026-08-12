package com.atcrew.community.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "커뮤니티 배너 수정 요청 — 모든 필드가 선택이며, 보내지 않은(null) 필드는 기존 값을 유지한다")
public record UpdateBannerRequest(
        @Schema(description = "배너 이미지 URL(최대 500자) — null이면 기존 값 유지",
                example = "https://cdn.atcrew.com/banners/spring-event-v2.png", nullable = true)
        @Size(max = 500) String imageUrl,

        @Schema(description = "배너 클릭 시 이동할 링크 URL(최대 500자) — null이면 기존 값 유지",
                example = "https://atcrew.com/events/spring-v2", nullable = true)
        @Size(max = 500) String linkUrl,

        @Schema(description = "노출 순서(0 이상) — null이면 기존 값 유지. 값을 바꾸면 기존 순번과 새 순번 사이의 "
                + "다른 배너들이 한 칸씩 당겨지거나 밀린다", example = "0", nullable = true)
        @Min(0) Integer sortOrder
) {
}
