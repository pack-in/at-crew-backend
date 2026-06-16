package com.atcrew.artwork;

import java.util.List;

public record UpdateArtworkCommand(
        List<String> imageKeys,
        Integer representativeImageIndex,
        ImageLayoutType imageLayoutType,
        String title,
        String description,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<String> genres,
        List<String> tags,
        AgeRating ageRating,
        List<String> tools,
        String workPeriodStart,
        String workPeriodEnd,
        List<MaterialData> materials
) {
}
