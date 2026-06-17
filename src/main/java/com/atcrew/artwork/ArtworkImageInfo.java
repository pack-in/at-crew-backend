package com.atcrew.artwork;

public record ArtworkImageInfo(
        String originalKey,
        String thumbKey,
        String thumbAdultKey,
        String originalAvifKey,
        ImageProcessingStatus processingStatus
) {
}
