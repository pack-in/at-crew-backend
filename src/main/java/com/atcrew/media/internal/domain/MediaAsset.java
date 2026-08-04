package com.atcrew.media.internal.domain;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaVariantProfile;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

@Entity
@Table(name = "media_assets")
@EntityListeners(AuditingEntityListener.class)
public class MediaAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) private MediaOwnerType ownerType;
    private String ownerId;
    private Integer ordinal;
    private String originalKey;
    private String thumbKey;
    private String thumbAdultKey;
    private String originalAvifKey;
    @Enumerated(EnumType.STRING) private MediaVariantProfile variantProfile;
    @Enumerated(EnumType.STRING) private MediaProcessingStatus processingStatus;
    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;

    protected MediaAsset() { }
    public static MediaAsset pending(MediaOwnerType ownerType, String ownerId, int ordinal, String originalKey,
                                     MediaVariantProfile variantProfile) {
        MediaAsset asset = new MediaAsset();
        asset.ownerType = ownerType; asset.ownerId = ownerId; asset.ordinal = ordinal;
        asset.originalKey = originalKey; asset.variantProfile = variantProfile;
        asset.processingStatus = MediaProcessingStatus.PENDING;
        return asset;
    }
    public void markProcessed(String thumbKey, String thumbAdultKey, String originalAvifKey,
                              MediaProcessingStatus status) {
        this.thumbKey = thumbKey; this.thumbAdultKey = thumbAdultKey; this.originalAvifKey = originalAvifKey;
        this.processingStatus = status;
    }
    public MediaOwnerType getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public Integer getOrdinal() { return ordinal; }
    public String getOriginalKey() { return originalKey; }
    public String getThumbKey() { return thumbKey; }
    public String getThumbAdultKey() { return thumbAdultKey; }
    public String getOriginalAvifKey() { return originalAvifKey; }
    public MediaVariantProfile getVariantProfile() { return variantProfile; }
    public MediaProcessingStatus getProcessingStatus() { return processingStatus; }
}
