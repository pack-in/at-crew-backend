package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.ImageProcessingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "artwork_images")
public class ArtworkImage {

    // 순수 내부 자식 행 — 외부에 노출되지 않으므로 대리키(auto-increment)로 충분하다(§4).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 양방향 매핑(Artwork.images의 mappedBy) — 단방향 @JoinColumn은 artwork_id NOT NULL 제약과 충돌한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artwork_id", nullable = false)
    private Artwork artwork;

    // 이미지 순서 — representativeImageIndex가 이 값을 기준으로 대표 이미지를 가리킨다.
    private Integer ordinal;

    private String originalKey;
    private String thumbKey;
    private String thumbAdultKey;
    private String originalAvifKey;

    @Enumerated(EnumType.STRING)
    private ImageProcessingStatus processingStatus;

    protected ArtworkImage() {
    }

    static ArtworkImage pending(Artwork artwork, int ordinal, String originalKey) {
        ArtworkImage img = new ArtworkImage();
        img.artwork = artwork;
        img.ordinal = ordinal;
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
