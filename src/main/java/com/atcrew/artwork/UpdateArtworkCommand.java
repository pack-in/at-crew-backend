package com.atcrew.artwork;

import com.atcrew.member.Language;

import java.util.List;

public record UpdateArtworkCommand(
        List<String> imageKeys,
        Integer representativeImageIndex,
        String thumbnailKey,
        ImageLayoutType imageLayoutType,
        String title,
        String description,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<Genre> genres,
        List<String> tags,
        AgeRating ageRating,
        List<Language> languages,
        List<String> tools,
        WorkDuration workDuration,
        Integer cutCount,
        List<String> videoLinks,
        List<MaterialData> materials
) {
}
