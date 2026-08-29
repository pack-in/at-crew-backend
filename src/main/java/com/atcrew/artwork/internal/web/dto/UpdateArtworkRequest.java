package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkCustomTagInfo;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.member.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateArtworkRequest(
        @Size(max = 30) List<String> imageKeys,
        @Min(0) Integer representativeImageIndex,
        String thumbnailKey,
        ImageLayoutType imageLayoutType,
        @Size(max = 100) String title,
        @Size(max = 500) String description,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<Genre> genres,
        // 담당 역할·장르 직접입력 값(업로드-R13). null이면 기존 값 유지, []이면 전체 삭제.
        @Size(max = 20) List<@NotNull ArtworkCustomTagInfo> customTags,
        @Size(max = 7) List<String> tags,
        AgeRating ageRating,
        // 수정 요청은 부분 반영이라 null이면 기존 값을 유지한다 — 전송하면 최대 4개까지.
        @Size(max = 4) List<Language> languages,
        List<String> tools,
        WorkDuration workDuration,
        Integer cutCount,
        @Size(max = 5) List<String> videoLinks,
        @Valid List<MaterialRequest> materials
) {
}
