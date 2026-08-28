package com.atcrew.artwork.internal.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignRequest(
        @Min(1) @Max(30) int count,
        @NotEmpty @Size(max = 30) List<String> contentTypes
) {
}
