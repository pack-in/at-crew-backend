package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.WorkDuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UploadArtworkRequest(
        @NotEmpty @Size(max = 20) List<String> imageKeys,
        @Min(0) int representativeImageIndex,
        String thumbnailKey,
        @NotNull ImageLayoutType imageLayoutType,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @NotNull ArtworkField artworkField,
        @NotNull CreativeType creativeType,
        @NotEmpty List<ArtworkRole> roles,
        List<Genre> genres,
        @Size(max = 7) List<String> tags,
        @NotNull AgeRating ageRating,
        @NotNull Visibility visibility,
        List<String> tools,
        WorkDuration workDuration,
        Integer cutCount,
        @Size(max = 5) List<String> videoLinks,
        @Valid List<MaterialRequest> materials
) {
}
