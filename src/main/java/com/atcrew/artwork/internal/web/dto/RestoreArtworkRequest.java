package com.atcrew.artwork.internal.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RestoreArtworkRequest(
        @NotEmpty List<String> artworkIds
) {
}
