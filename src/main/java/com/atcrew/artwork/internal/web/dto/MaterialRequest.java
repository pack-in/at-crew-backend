package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.MaterialTarget;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MaterialRequest(
        @NotBlank String name,
        List<MaterialTarget> targets,
        List<String> attachmentKeys,
        List<String> links
) {
}
