package com.atcrew.search;

import com.atcrew.artwork.AgeRating;

import java.time.Instant;

/**
 * 검색 결과 카드 — 게시글 유형(postType)에 관계없이 공통으로 표시되는 최소 필드만 담는다.
 * 유형별 전용 필드(구인글 마감일 등)는 recruit 모듈 구현 시 확장한다.
 */
public record SearchResultItem(
        String id,
        PostType postType,
        String title,
        String thumbnailKey,
        String thumbnailAdultKey,
        String authorId,
        String authorName,
        String authorHandle,
        AgeRating ageRating,
        Instant createdAt
) {
}
