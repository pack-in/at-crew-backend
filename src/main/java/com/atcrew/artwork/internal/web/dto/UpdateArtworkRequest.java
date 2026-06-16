package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateArtworkRequest(
        @Size(max = 20) List<String> imageKeys,
        @Min(0) Integer representativeImageIndex,
        ImageLayoutType imageLayoutType,
        @Size(max = 100) String title,
        @Size(max = 2000) String description,
        ArtworkField artworkField,
        CreativeType creativeType,
        List<ArtworkRole> roles,
        List<String> genres,
        @Size(max = 7) List<String> tags,
        AgeRating ageRating,
        List<String> tools,
        String workPeriodStart,
        String workPeriodEnd,
        @Valid List<MaterialRequest> materials
) {
}
