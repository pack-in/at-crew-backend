package com.atcrew.artwork;

import java.time.Instant;

public record BookmarkFolderInfo(
        String id,
        String name,
        int sortOrder,
        Instant createdAt
) {
}
