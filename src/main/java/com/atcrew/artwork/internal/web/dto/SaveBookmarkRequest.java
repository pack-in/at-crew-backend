package com.atcrew.artwork.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveBookmarkRequest(
        @NotBlank String artworkId,
        String folderId
) {
}
