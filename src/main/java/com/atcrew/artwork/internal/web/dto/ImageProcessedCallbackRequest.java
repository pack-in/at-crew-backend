package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.ImageProcessingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImageProcessedCallbackRequest(
        @NotBlank String artworkId,
        @NotBlank String imageKey,
        String thumbKey,
        String thumbAdultKey,
        String originalAvifKey,
        @NotNull ImageProcessingStatus status
) {
}
