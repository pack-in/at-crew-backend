package com.atcrew.artwork;

import java.util.List;

public record UploadArtworkCommand(
        List<String> imageKeys,
        int representativeImageIndex,
        ImageLayoutType imageLayoutType,
        String title,
        String description,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<String> genres,
        List<String> tags,
        AgeRating ageRating,
        Visibility visibility,
        List<String> tools,
        String workPeriodStart,
        String workPeriodEnd,
        List<MaterialData> materials
) {
}
