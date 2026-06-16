package com.atcrew.artwork;

import java.time.Instant;
import java.util.List;

public record ArtworkSummaryInfo(
        String id,
        String authorId,
        String authorName,
        String authorHandle,
        String title,
        String thumbKey,
        String thumbAdultKey,
        ArtworkField artworkField,
        List<String> tags,
        AgeRating ageRating,
        Visibility visibility,
        ArtworkStatus status,
        Instant createdAt
) {
}
