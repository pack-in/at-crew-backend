package com.atcrew.artwork;

import com.atcrew.member.Language;

import java.time.Instant;
import java.util.List;

public record ArtworkInfo(
        String id,
        String authorId,
        String authorName,
        String authorHandle,
        String title,
        String description,
        List<ArtworkImageInfo> images,
        int representativeImageIndex,
        String thumbnailKey,
        ImageLayoutType imageLayoutType,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<Genre> genres,
        List<ArtworkCustomTagInfo> customTags,
        List<String> tags,
        List<String> tools,
        WorkDuration workDuration,
        Integer cutCount,
        List<String> videoLinks,
        AgeRating ageRating,
        // 게시물 작성·노출 언어(업로드-R30). 마이그레이션 이전 작품은 비어 있다
        List<Language> languages,
        Visibility visibility,
        // 라이브 포트폴리오(작가 페이지 + 최신 반영형) 편입 여부 —
        // visibility가 PRIVATE여도 이 값이 true면 포트폴리오 한정 공개라 완전 비공개가 아니다
        // (docs/design/portfolio-module-design.md §1.2, §5.4)
        boolean portfolioIncluded,
        // 운영 정책·법적 조치에 따른 외부 노출 중단 여부(마이페이지_작가-R39) —
        // 차단된 작품은 작성자 본인에게만 조회되며 화면에는 차단 안내 배지를 노출한다
        boolean blocked,
        List<MaterialInfo> materials,
        ArtworkStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
