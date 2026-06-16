package com.atcrew.artwork;

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
        ImageLayoutType imageLayoutType,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<String> genres,
        List<String> tags,
        List<String> tools,
        String workPeriodStart,
        String workPeriodEnd,
        AgeRating ageRating,
        Visibility visibility,
        List<MaterialInfo> materials,
        ArtworkStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
