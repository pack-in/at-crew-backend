package com.atcrew.artwork.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBookmarkFolderRequest(
        @NotBlank @Size(max = 20) String name
) {
}
