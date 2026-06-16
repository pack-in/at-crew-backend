package com.atcrew.artwork;

import java.util.List;

public record ArtworkPermanentlyDeletedEvent(
        String artworkId,
        List<String> allImageKeys
) {
}
