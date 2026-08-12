package com.atcrew.recruit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 커뮤니티 "구인글" 탭 카드 응답. community 모듈이 {@link RecruitService}를 통해 소비한다
 * (docs/design/recruit-module-design.md §6.1 — community 소유였던 이 record를 recruit으로 이관).
 * recruit 콘텐츠는 성인물 게이팅 대상이 아니므로(설계 §7) ageRating 필드를 두지 않는다.
 */
@Schema(description = "커뮤니티 구인글 탭 카드. 게시 상태(PUBLISHED)인 공고만 조회 대상이라 closed는 "
        + "항상 false로 내려간다 — 실제 마감 표시 UI에는 쓰이지 않는 필드다. 끌어올리기(boost)를 적용한 "
        + "공고는 최신순 목록 상단에 고정 노출된다(카드 필드로는 구분되지 않음).")
public record CommunityJobPostingCardInfo(
        @Schema(description = "구인글 ID") String id,
        @Schema(description = "썸네일 이미지 URL", nullable = true) String thumbnailUrl,
        @Schema(description = "공고 제목") String title,
        @Schema(description = "회사명") String companyName,
        @Schema(description = "작성자(작성 계정) 표시명") String authorName,
        @Schema(description = "마감일(yyyy-MM-dd) — null이면 상시모집", nullable = true) LocalDate deadline,
        @Schema(description = "마감 여부 — PUBLISHED 상태만 조회하므로 이 목록에서는 항상 false") boolean closed
) {
}
