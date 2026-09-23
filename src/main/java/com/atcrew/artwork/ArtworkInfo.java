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
        // 사용자 지정 썸네일의 업로드 key — 식별용이다. 수정 요청에 그대로 다시 보내는 값이며, 변환이 끝나면 R2에서
        // 원본이 지워지므로 이미지를 불러오는 데 쓰면 안 된다(표시용은 thumbnailImage).
        String thumbnailKey,
        // 사용자 지정 썸네일의 변환 결과 — 표시는 thumbKey, 변환 전이면 originalKey. 썸네일을 변환하기 전에
        // 올라온 작품은 null이라 thumbnailKey를 그대로 쓴다. 카드 판정은 ArtworkCardThumbnail에 있다.
        ArtworkImageInfo thumbnailImage,
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
