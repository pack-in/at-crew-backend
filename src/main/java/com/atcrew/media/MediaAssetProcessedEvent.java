package com.atcrew.media;

public record MediaAssetProcessedEvent(MediaOwnerType ownerType, String ownerId, String imageKey,
                                       String thumbKey, String thumbAdultKey, String originalAvifKey,
                                       MediaProcessingStatus status) { }
