package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "artworks")
public class Artwork {

    @Id
    private String id;

    private String authorId;
    private String title;
    private String description;

    private List<ArtworkImage> images;
    private int representativeImageIndex;
    private String thumbnailKey;
    private ImageLayoutType imageLayoutType;

    private ArtworkField artworkField;
    private CreativeType creativeType;
    private List<ArtworkRole> roles;
    private List<String> genres;
    private List<String> tags;

    private List<String> tools;
    private WorkDuration workDuration;
    private Integer cutCount;
    private List<String> videoLinks;

    private AgeRating ageRating;
    private Visibility visibility;
    private Visibility visibilityBeforeDelete;

    private List<Material> materials;

    private ArtworkStatus status;
    private Instant deletedAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    protected Artwork() {
    }

    public static Artwork create(String authorId, String title, String description,
                                 List<String> imageKeys, int representativeImageIndex,
                                 String thumbnailKey,
                                 ImageLayoutType imageLayoutType, ArtworkField artworkField,
                                 CreativeType creativeType, List<ArtworkRole> roles,
                                 List<String> genres, List<String> tags,
                                 AgeRating ageRating, Visibility visibility,
                                 List<String> tools, WorkDuration workDuration,
                                 Integer cutCount, List<String> videoLinks,
                                 List<Material> materials) {
        if (imageKeys == null || imageKeys.isEmpty() || imageKeys.size() > 20) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT);
        }
        if (representativeImageIndex < 0 || representativeImageIndex >= imageKeys.size()) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_REPRESENTATIVE_INDEX);
        }
        Artwork artwork = new Artwork();
        artwork.authorId = authorId;
        artwork.title = title;
        artwork.description = description;
        artwork.images = imageKeys.stream().map(ArtworkImage::pending).toList();
        artwork.representativeImageIndex = representativeImageIndex;
        artwork.thumbnailKey = thumbnailKey;
        artwork.imageLayoutType = imageLayoutType;
        artwork.artworkField = artworkField;
        artwork.creativeType = creativeType;
        artwork.roles = new ArrayList<>(roles != null ? roles : List.of());
        artwork.genres = new ArrayList<>(genres != null ? genres : List.of());
        artwork.tags = new ArrayList<>(tags != null ? tags : List.of());
        artwork.ageRating = ageRating;
        artwork.visibility = visibility;
        artwork.tools = new ArrayList<>(tools != null ? tools : List.of());
        artwork.workDuration = workDuration;
        artwork.cutCount = cutCount;
        artwork.videoLinks = new ArrayList<>(videoLinks != null ? videoLinks : List.of());
        artwork.materials = new ArrayList<>(materials != null ? materials : List.of());
        artwork.status = ArtworkStatus.PROCESSING;
        return artwork;
    }

    public void assertOwner(String memberId) {
        if (!authorId.equals(memberId)) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_ACCESS_DENIED);
        }
    }

    public void assertReady() {
        if (status != ArtworkStatus.READY) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_READY);
        }
    }

    public void assertDeleted() {
        if (status != ArtworkStatus.DELETED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_DELETED);
        }
    }

    public void updateDetails(String title, String description,
                              ImageLayoutType imageLayoutType, Integer representativeImageIndex,
                              String thumbnailKey,
                              ArtworkField artworkField, CreativeType creativeType,
                              List<ArtworkRole> roles, List<String> genres, List<String> tags,
                              AgeRating ageRating, List<String> tools,
                              WorkDuration workDuration, Integer cutCount,
                              List<String> videoLinks, List<Material> materials) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (imageLayoutType != null) this.imageLayoutType = imageLayoutType;
        if (representativeImageIndex != null) {
            if (representativeImageIndex < 0 || representativeImageIndex >= this.images.size()) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_REPRESENTATIVE_INDEX);
            }
            this.representativeImageIndex = representativeImageIndex;
        }
        if (thumbnailKey != null) this.thumbnailKey = thumbnailKey;
        if (artworkField != null) this.artworkField = artworkField;
        if (creativeType != null) this.creativeType = creativeType;
        if (roles != null) this.roles = new ArrayList<>(roles);
        if (genres != null) this.genres = new ArrayList<>(genres);
        if (tags != null) this.tags = new ArrayList<>(tags);
        if (ageRating != null) this.ageRating = ageRating;
        if (tools != null) this.tools = new ArrayList<>(tools);
        if (workDuration != null) this.workDuration = workDuration;
        if (cutCount != null) this.cutCount = cutCount;
        if (videoLinks != null) this.videoLinks = new ArrayList<>(videoLinks);
        if (materials != null) this.materials = new ArrayList<>(materials);
    }

    public List<ArtworkImage> replaceImages(List<String> newImageKeys, int newRepresentativeIndex) {
        if (newImageKeys == null || newImageKeys.isEmpty() || newImageKeys.size() > 20) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT);
        }
        if (newRepresentativeIndex < 0 || newRepresentativeIndex >= newImageKeys.size()) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_REPRESENTATIVE_INDEX);
        }
        List<ArtworkImage> orphaned = new ArrayList<>(this.images);
        this.images = newImageKeys.stream().map(ArtworkImage::pending).toList();
        this.representativeImageIndex = newRepresentativeIndex;
        this.status = ArtworkStatus.PROCESSING;
        return orphaned;
    }

    public void changeVisibility(Visibility visibility) {
        assertReady();
        this.visibility = visibility;
    }

    // 탈퇴 이벤트 처리용 — READY 상태 체크 없이 강제 비공개
    public void forcePrivate() {
        if (this.visibility != Visibility.PRIVATE) {
            this.visibility = Visibility.PRIVATE;
        }
    }

    public void moveToTrash() {
        if (status == ArtworkStatus.DELETED) {
            return;
        }
        this.visibilityBeforeDelete = this.visibility;
        this.visibility = Visibility.PRIVATE;
        this.status = ArtworkStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    public void restore() {
        assertDeleted();
        this.status = ArtworkStatus.READY;
        this.visibility = this.visibilityBeforeDelete != null ? this.visibilityBeforeDelete : Visibility.PRIVATE;
        this.visibilityBeforeDelete = null;
        this.deletedAt = null;
    }

    public void markImageProcessed(String originalKey, String thumbKey,
                                   String thumbAdultKey, String originalAvifKey,
                                   boolean success) {
        images.stream()
                .filter(img -> originalKey.equals(img.getOriginalKey()))
                .findFirst()
                .ifPresent(img -> {
                    if (success) {
                        img.markDone(thumbKey, thumbAdultKey, originalAvifKey);
                    } else {
                        img.markFailed();
                    }
                });
        // 처리 중인 이미지가 없고 하나라도 성공한 경우 READY로 전환 (부분 실패 허용)
        boolean noneProcessing = images.stream().noneMatch(ArtworkImage::isPending);
        boolean anyDone = images.stream().anyMatch(ArtworkImage::isDone);
        if (noneProcessing && anyDone) {
            this.status = ArtworkStatus.READY;
        }
    }

    public boolean isVisibleTo(String viewerMemberId) {
        if (status != ArtworkStatus.READY) return false;
        if (authorId.equals(viewerMemberId)) return true;
        return visibility == Visibility.PUBLIC || visibility == Visibility.LINK_ONLY;
    }

    public ArtworkImage getRepresentativeImage() {
        if (images == null || images.isEmpty()) return null;
        int idx = Math.min(representativeImageIndex, images.size() - 1);
        return images.get(idx);
    }

    public String getId() { return id; }
    public String getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<ArtworkImage> getImages() { return List.copyOf(images); }
    public int getRepresentativeImageIndex() { return representativeImageIndex; }
    public String getThumbnailKey() { return thumbnailKey; }
    public ImageLayoutType getImageLayoutType() { return imageLayoutType; }
    public ArtworkField getArtworkField() { return artworkField; }
    public CreativeType getCreativeType() { return creativeType; }
    public List<ArtworkRole> getRoles() { return List.copyOf(roles); }
    public List<String> getGenres() { return List.copyOf(genres); }
    public List<String> getTags() { return List.copyOf(tags); }
    public List<String> getTools() { return List.copyOf(tools); }
    public WorkDuration getWorkDuration() { return workDuration; }
    public Integer getCutCount() { return cutCount; }
    public List<String> getVideoLinks() { return videoLinks != null ? List.copyOf(videoLinks) : List.of(); }
    public AgeRating getAgeRating() { return ageRating; }
    public Visibility getVisibility() { return visibility; }
    public List<Material> getMaterials() { return List.copyOf(materials); }
    public ArtworkStatus getStatus() { return status; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
