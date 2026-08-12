package com.atcrew.community.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "커뮤니티 배너 등록 요청")
public record CreateBannerRequest(
        @Schema(description = "배너를 소유할 회원 ID — 존재하지 않으면 404 MEMBER_NOT_FOUND",
                example = "019ff382-ccc3-7c5d-b937-385d1da00d6f", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String memberId,

        @Schema(description = "배너 이미지 URL", example = "https://cdn.atcrew.com/banners/spring-event.png",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String imageUrl,

        @Schema(description = "배너 클릭 시 이동할 링크 URL", example = "https://atcrew.com/events/spring",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String linkUrl,

        @Schema(description = "노출 순서. 미지정 시 마지막 순번 다음 값이 자동 부여된다(배너가 없으면 0). "
                + "값을 지정하면 그 순번 이후의 기존 배너들이 한 칸씩 뒤로 밀린다",
                example = "0", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer sortOrder
) {
}
