package com.atcrew.portfolio.internal.web.dto;

import com.atcrew.portfolio.ReflectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 공유 포트폴리오 생성 요청 (docs/design/portfolio-module-design.md §4).
 * 작가 페이지는 생성 API가 없다 — 최초 조회 시 lazy 생성된다.
 */
@Schema(description = "공유 포트폴리오 생성 요청 — 작가 페이지는 이 API로 만들 수 없다(최초 조회 시 자동 생성)")
public record CreatePortfolioRequest(
        @Schema(description = "공유 포트폴리오 제목 (필수, 최대 100자, 앞뒤 공백은 제거된다)",
                example = "라이브 공유 포트폴리오", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String title,
        // 공유 포트폴리오 제목 — 작가 페이지와 달리 필수다

        @Schema(description = "반영 유형 (필수) — LIVE(최신 반영형) / SNAPSHOT(고정형). 생성 후 전환할 수 없다",
                example = "LIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ReflectionType reflectionType,
        // 반영 유형 — LIVE(최신 반영형) / SNAPSHOT(고정형)

        @Schema(description = "담을 작품 ID 목록 (최대 100개) — 본인 소유의 삭제되지 않은 작품이어야 하며 "
                + "하나라도 아니면 404(ARTWORK_NOT_FOUND). null이나 빈 배열로 0개 생성이 가능하다. "
                + "실제 순서는 요청 순서가 아니라 작품 업로드순(오래된순)으로 정렬된다",
                example = "[\"019ff382-bd4a-7045-80ac-7430bd0832c7\",\"019ff382-bd56-7e7e-830b-40e3fbd6a0d3\"]")
        @Size(max = 100) List<@NotBlank @Size(max = 36) String> artworkIds
        // 담을 작품 ID 목록 — 0개로 생성할 수 있다(복제 시 자동 선택이 0개일 수 있음)
) {
}
