package com.atcrew.recruit.internal.domain;

import com.atcrew.media.MediaProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.util.List;

/**
 * 구인글/팀원모집글/구직글 이미지 자식 행의 공통 정의 (docs/design/media-module-design.md §10.1).
 *
 * <p>세 게시글 종류가 같은 컬럼 구성을 쓰므로 매핑을 공유하고, 테이블만 하위 엔티티에서 지정한다.
 * {@code thumbAdultKey}는 없다 — recruit은 {@code variantProfile=STANDARD} 고정이라 성인물 blur
 * 썸네일 변환을 요청하지 않는다(§3).
 */
@MappedSuperclass
public abstract class RecruitPostingImage {

    // 순수 내부 자식 행 — 외부에 노출되지 않으므로 대리키로 충분하다(artwork_images와 동일).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "posting_id", length = 36, nullable = false)
    private String postingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private RecruitImageRole role;

    // THUMBNAIL=0, REFERENCE=1..n — media_assets.ordinal과 같은 축을 쓴다.
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "original_key", length = 500, nullable = false)
    private String originalKey;

    @Column(name = "thumb_key", length = 500)
    private String thumbKey;

    @Column(name = "original_avif_key", length = 500)
    private String originalAvifKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 30, nullable = false)
    private MediaProcessingStatus processingStatus;

    protected RecruitPostingImage() {
    }

    protected void init(String postingId, RecruitImageRole role, int ordinal, String originalKey) {
        this.postingId = postingId;
        this.role = role;
        this.ordinal = ordinal;
        this.originalKey = originalKey;
        this.processingStatus = MediaProcessingStatus.PENDING;
    }

    /**
     * Worker 처리 결과를 반영한다. {@code status}는 media 모듈이 그대로 전달한 DONE/FAILED다.
     */
    public void markProcessed(String thumbKey, String originalAvifKey, MediaProcessingStatus status) {
        this.thumbKey = thumbKey;
        this.originalAvifKey = originalAvifKey;
        this.processingStatus = status;
    }

    /**
     * 응답에 실을 키 — 변환이 끝났으면 AVIF 원본, 아직 처리 중이면 업로드된 원본으로 폴백한다(§10.4).
     */
    public String displayKey() {
        return originalAvifKey != null && !originalAvifKey.isBlank() ? originalAvifKey : originalKey;
    }

    public boolean isPending() {
        return processingStatus == MediaProcessingStatus.PENDING;
    }

    public boolean isDone() {
        return processingStatus == MediaProcessingStatus.DONE;
    }

    /**
     * 게시글을 READY로 넘길 수 있는지 판정한다 — <b>"PENDING이 하나도 없고 DONE이 하나 이상"</b>
     * (설계 §5·§10.3). artwork의 {@code Artwork.markImageProcessed}와 같은 규칙으로 부분 실패를 허용한다.
     * "전부 DONE"으로 판정하면 이미지 하나가 FAILED일 때 영원히 READY로 넘어가지 못한다.
     */
    public static boolean readyFor(List<? extends RecruitPostingImage> images) {
        return images.stream().noneMatch(RecruitPostingImage::isPending)
                && images.stream().anyMatch(RecruitPostingImage::isDone);
    }

    public String getPostingId() { return postingId; }
    public RecruitImageRole getRole() { return role; }
    public int getOrdinal() { return ordinal; }
    public String getOriginalKey() { return originalKey; }
    public String getThumbKey() { return thumbKey; }
    public String getOriginalAvifKey() { return originalAvifKey; }
    public MediaProcessingStatus getProcessingStatus() { return processingStatus; }
}
