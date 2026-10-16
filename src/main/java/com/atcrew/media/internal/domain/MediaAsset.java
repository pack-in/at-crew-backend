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
    // 소유자가 정하는 슬롯 이름(recruit의 THUMBNAIL/REFERENCE). media는 값을 해석하지 않고 보관·반환만 한다.
    @Column(name = "slot_role", length = 30) private String slotRole;
    private String originalKey;
    private String thumbKey;
    private String thumbAdultKey;
    private String originalAvifKey;
    // 공유 링크 미리보기(OG) 1200×630 JPEG — 카드 썸네일 자산(THUMBNAIL_WITH_ADULT_BLUR)에만 생긴다. 생성 전·실패면 null.
    private String ogKey;
    private String ogAdultKey;
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
        return pending(ownerType, ownerId, ordinal, null, originalKey, variantProfile, qualityTier);
    }

    public static MediaAsset pending(MediaOwnerType ownerType, String ownerId, int ordinal, String slotRole,
                                     String originalKey, MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        MediaAsset asset = new MediaAsset();
        asset.ownerType = ownerType; asset.ownerId = ownerId; asset.ordinal = ordinal; asset.slotRole = slotRole;
        asset.originalKey = originalKey; asset.variantProfile = variantProfile; asset.qualityTier = qualityTier;
        asset.processingStatus = MediaProcessingStatus.PENDING;
        return asset;
    }
    /** 목록 안에서의 자리만 옮긴다 — 변환 결과와 상태는 그대로 둔다(부분 교체 시 유지되는 이미지). */
    public void relocate(int ordinal, String slotRole) {
        this.ordinal = ordinal; this.slotRole = slotRole;
    }

    public void markProcessed(String thumbKey, String thumbAdultKey, String originalAvifKey,
                              MediaProcessingStatus status) {
        markProcessed(thumbKey, thumbAdultKey, originalAvifKey, null, null, status);
    }

    public void markProcessed(String thumbKey, String thumbAdultKey, String originalAvifKey,
                              String ogKey, String ogAdultKey, MediaProcessingStatus status) {
        this.thumbKey = thumbKey; this.thumbAdultKey = thumbAdultKey; this.originalAvifKey = originalAvifKey;
        this.ogKey = ogKey; this.ogAdultKey = ogAdultKey;
        this.processingStatus = status;
    }

    /**
     * 기존 자산에 OG key만 붙인다(백필). 썸네일 key와 처리 상태는 건드리지 않는다 — OG 전용 콜백은 썸네일 필드를 싣지
     * 않으므로 {@link #markProcessed}로 받으면 썸네일 key가 null로 덮인다.
     *
     * <p>null은 "이번에 만들지 못했다"는 뜻이라 기존 값을 지우지 않는다. 지우면 이미 R2에 있는 OG 파일이 어디서도 참조되지
     * 않아 정리 경로에서도 빠진다. OG key는 원본 key에서 정해지므로 값이 오면 기존과 같다.
     */
    public void attachOgKeys(String ogKey, String ogAdultKey) {
        if (ogKey != null) this.ogKey = ogKey;
        if (ogAdultKey != null) this.ogAdultKey = ogAdultKey;
    }
    public Long getId() { return id; }
    public MediaOwnerType getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public Integer getOrdinal() { return ordinal; }
    public String getSlotRole() { return slotRole; }
    public String getOriginalKey() { return originalKey; }
    public String getThumbKey() { return thumbKey; }
    public String getThumbAdultKey() { return thumbAdultKey; }
    public String getOriginalAvifKey() { return originalAvifKey; }
    public String getOgKey() { return ogKey; }
    public String getOgAdultKey() { return ogAdultKey; }
    public MediaVariantProfile getVariantProfile() { return variantProfile; }
    public MediaQualityTier getQualityTier() { return qualityTier; }
    public MediaProcessingStatus getProcessingStatus() { return processingStatus; }
}
