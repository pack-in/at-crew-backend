package com.atcrew.artwork;

import java.util.List;

public record UploadArtworkCommand(
        List<String> imageKeys,
        int representativeImageIndex,
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
        Visibility visibility,
        List<String> tools,
        WorkDuration workDuration,
        Integer cutCount,
        List<String> videoLinks,
        List<MaterialData> materials
) {
}
