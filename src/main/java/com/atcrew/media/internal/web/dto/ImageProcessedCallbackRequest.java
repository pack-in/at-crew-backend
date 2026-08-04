package com.atcrew.media.internal.web.dto;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageProcessedCallbackRequest(@NotNull MediaOwnerType ownerType, @NotBlank String ownerId,
                                            @NotBlank String imageKey, String thumbKey, String thumbAdultKey,
                                            String originalAvifKey, @NotNull MediaProcessingStatus status) { }
