package com.atcrew.media;

public record MediaAssetInfo(String originalKey, String thumbKey, String thumbAdultKey,
                             String originalAvifKey, MediaProcessingStatus status) { }
