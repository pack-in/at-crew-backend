package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.ImageProcessingStatus;

public class ArtworkImage {

    private String originalKey;
    private String thumbKey;
    private String thumbAdultKey;
    private String originalAvifKey;
    private ImageProcessingStatus processingStatus;

    protected ArtworkImage() {
    }

    public static ArtworkImage pending(String originalKey) {
        ArtworkImage img = new ArtworkImage();
        img.originalKey = originalKey;
        img.processingStatus = ImageProcessingStatus.PENDING;
        return img;
    }

    public void markDone(String thumbKey, String thumbAdultKey, String originalAvifKey) {
        this.thumbKey = thumbKey;
        this.thumbAdultKey = thumbAdultKey;
        this.originalAvifKey = originalAvifKey;
        this.processingStatus = ImageProcessingStatus.DONE;
    }

    public void markFailed() {
        this.processingStatus = ImageProcessingStatus.FAILED;
    }

    public boolean isPending() {
        return processingStatus == ImageProcessingStatus.PENDING;
    }

    public boolean isDone() {
        return processingStatus == ImageProcessingStatus.DONE;
    }

    public String getOriginalKey() { return originalKey; }
    public String getThumbKey() { return thumbKey; }
    public String getThumbAdultKey() { return thumbAdultKey; }
    public String getOriginalAvifKey() { return originalAvifKey; }
    public ImageProcessingStatus getProcessingStatus() { return processingStatus; }
}
