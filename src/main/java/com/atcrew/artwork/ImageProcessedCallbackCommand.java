package com.atcrew.artwork;

public record ImageProcessedCallbackCommand(
        String artworkId,
        String imageKey,
        String thumbKey,
        String thumbAdultKey,
        String originalAvifKey,
        ImageProcessingStatus status
) {
}
