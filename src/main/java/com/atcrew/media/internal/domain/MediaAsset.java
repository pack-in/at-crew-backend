package com.atcrew.media.internal.domain;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaVariantProfile;
import jakarta.persistence.Column;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
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
    // 업로드 시점 플랜으로 확정된 변환 화질 — 재시도가 최초와 같은 결과를 내도록 함께 보관한다.
    @Enumerated(EnumType.STRING) private MediaQualityTier qualityTier;
    @Enumerated(EnumType.STRING) private MediaProcessingStatus processingStatus;
    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    // 변경 주체(이슈 #138). 회원 ID이거나 SYSTEM, 운영자 수동 UPDATE는 "ops:<담당자>".
    // 기록 시작 전 행은 NULL로 남는다.
    @LastModifiedBy
    @Column(name = "last_modified_by", length = 64)
    private String lastModifiedBy;

    protected MediaAsset() { }
    public static MediaAsset pending(MediaOwnerType ownerType, String ownerId, int ordinal, String originalKey,
                                     MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        MediaAsset asset = new MediaAsset();
        asset.ownerType = ownerType; asset.ownerId = ownerId; asset.ordinal = ordinal;
        asset.originalKey = originalKey; asset.variantProfile = variantProfile; asset.qualityTier = qualityTier;
        asset.processingStatus = MediaProcessingStatus.PENDING;
        return asset;
    }
    /**
     * 교체 뒤에도 남는 이미지 — 새 순서로 옮기되 처리 결과(상태·변형본 key·화질)는 그대로 넘겨받는다. Worker는 변환을
     * 마치면 raw를 지우므로 다시 트리거하면 "원본 없음"으로 실패한다.
     */
    public static MediaAsset carriedOver(MediaAsset previous, int ordinal) {
        MediaAsset asset = pending(previous.ownerType, previous.ownerId, ordinal, previous.originalKey,
                previous.variantProfile, previous.qualityTier);
        asset.markProcessed(previous.thumbKey, previous.thumbAdultKey, previous.originalAvifKey, previous.processingStatus);
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
    public MediaQualityTier getQualityTier() { return qualityTier; }
    public MediaProcessingStatus getProcessingStatus() { return processingStatus; }
}
