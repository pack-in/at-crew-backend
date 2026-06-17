package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.Visibility;
import jakarta.validation.constraints.NotNull;

public record UpdateVisibilityRequest(
        @NotNull Visibility visibility
) {
}
