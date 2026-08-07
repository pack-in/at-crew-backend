package com.atcrew.recruit;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;

import java.time.Instant;
import java.util.List;

/**
 * 검색 색인 재조회 전용 응답 — search 모듈이 {@link RecruitService#getPostForIndexing}/
 * {@link RecruitService#getPostsForReindex}로 얻는 최소 필드 집합이다(docs/design/search-module-design.md §5.1).
 *
 * <p>구인글·팀원모집글·구직글 3종의 상태(status) 값은 타입마다 다른 enum이라 문자열로 통일한다 —
 * search 모듈은 {@code "PUBLISHED".equals(status())}로만 판단하면 되므로 원본 enum 타입을 알 필요가 없다.
 * 구직글은 썸네일이 없어 thumbnailKey가 항상 null이다.
 */
public record RecruitIndexInfo(
        String id,
        RecruitPostType postType,
        String title,
        List<ArtworkRole> roles,
        List<Genre> genres,
        String authorId,
        String authorName,
        String thumbnailKey,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
