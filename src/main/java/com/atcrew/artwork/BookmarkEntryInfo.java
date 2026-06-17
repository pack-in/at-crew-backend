package com.atcrew.artwork;

import java.time.Instant;

public record BookmarkEntryInfo(
        String id,
        String artworkId,
        String folderId,
        Instant savedAt,
        ArtworkSummaryInfo artwork
) {
}
