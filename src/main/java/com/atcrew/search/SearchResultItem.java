package com.atcrew.search;

import com.atcrew.artwork.AgeRating;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 검색 결과 카드 — 게시글 유형(postType)에 관계없이 공통으로 표시되는 최소 필드만 담는다.
 * 유형별 전용 필드(구인글 마감일 등)는 화면 요구가 확정되면 확장한다.
 *
 * <p>recruit 소유 유형(구인글·구직글·팀원모집글)에는 성인 썸네일·작성자 핸들·연령 등급이 없어 항상 null이다.
 */
@Schema(description = "검색 결과 카드 — 게시글 유형에 관계없이 공통 필드만 담는다. "
        + "유형별 상세는 postType에 맞는 상세 조회 API로 확인한다")
public record SearchResultItem(
        @Schema(description = "게시글 ID — postType에 해당하는 상세 조회 API에 그대로 사용한다",
                example = "019ff382-ccdc-71bb-bccb-6a3c35d33978")
        String id,

        @Schema(description = "게시글 유형 — 어느 모듈의 글인지 구분한다", example = "PORTFOLIO")
        PostType postType,

        @Schema(description = "게시글 제목", example = "판타지 일러스트 커미션 모음")
        String title,

        @Schema(description = "썸네일 이미지 키(R2 오브젝트 키) — 썸네일이 없으면 null", nullable = true,
                example = "artworks/019ff382/thumb.webp")
        String thumbnailKey,

        @Schema(description = "성인물 블러 처리용 썸네일 키 — 전체연령가이거나 recruit 소유 유형(구인글·구직글·팀원모집글)이면 null",
                nullable = true, example = "artworks/019ff382/thumb-adult.webp")
        String thumbnailAdultKey,

        @Schema(description = "작성자 회원 ID", example = "019ff382-ccc3-7c5d-b937-385d1da00d6f")
        String authorId,

        @Schema(description = "작성자 표시명", example = "홍길동")
        String authorName,

        @Schema(description = "작성자 핸들(@핸들) — 포트폴리오만 제공하며 recruit 소유 유형은 항상 null",
                nullable = true, example = "user_f997ce20")
        String authorHandle,

        @Schema(description = "연령 등급 — 포트폴리오만 제공하며 recruit 소유 유형은 연령 게이팅 대상이 아니라 항상 null",
                nullable = true, example = "ALL")
        AgeRating ageRating,

        @Schema(description = "게시글 등록 시각 (UTC, ISO 8601) — 최신순 정렬 기준",
                example = "2026-08-12T01:07:43.452980Z")
        Instant createdAt
) {
}
