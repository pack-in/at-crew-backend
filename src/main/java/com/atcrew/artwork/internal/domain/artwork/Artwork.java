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
import com.atcrew.common.id.UuidV7Generator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "artworks")
@EntityListeners(AuditingEntityListener.class)
public class Artwork implements Persistable<String> {

    @Id
    private String id;

    private String authorId;
    private String title;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.10) — 65535까지의 길이를 지정해
    // MariaDB/MySQL 방언이 VARCHAR가 아닌 TEXT 컬럼 타입을 기대하도록 한다(V1의 실제 컬럼 타입과 일치).
    @Column(length = 65535)
    private String description;

    // 양방향 매핑(mappedBy) — 단방향 @OneToMany+@JoinColumn은 Hibernate가 INSERT 시 FK 없이 먼저 쓰고
    // 뒤이어 UPDATE로 채우는 2단계 패턴이라 artwork_id NOT NULL 제약과 충돌한다
    // (docs/design/mariadb-migration-design.md §11/§12에서 이미 발견된 함정과 동일 패턴).
    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<ArtworkImage> images = new ArrayList<>();

    @Column(name = "representative_image_index")
    private int representativeImageIndex;

    private String thumbnailKey;

    @Enumerated(EnumType.STRING)
    private ImageLayoutType imageLayoutType;

    @Enumerated(EnumType.STRING)
    private ArtworkField artworkField;

    @Enumerated(EnumType.STRING)
    private CreativeType creativeType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artwork_roles", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<ArtworkRole> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artwork_genres", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    private Set<String> genres = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artwork_tags", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    private Set<String> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artwork_tools", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    private Set<String> tools = new HashSet<>();

    // WorkDuration(공개 API record, com.atcrew.artwork)은 JPA 임베더블로 쓸 수 없어(record 미지원)
    // TermsAgreement와 동일한 방식으로 컬럼 4개로 펼쳐 저장하고 getter/setter에서 record로 조립/분해한다.
    // V1의 단일 work_duration VARCHAR(30) 컬럼은 구조화된 4필드 값객체에 맞지 않아 V4에서 컬럼 4개로 교체했다.
    private Integer workDurationMonths;
    private Integer workDurationDays;
    private Integer workDurationHours;
    private Integer workDurationMinutes;

    private Integer cutCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "video_links")
    private List<String> videoLinks = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    private Visibility visibilityBeforeDelete;

    // images와 동일한 이유로 양방향 매핑 (§11/§12 패턴)
    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<Material> materials = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ArtworkStatus status;

    private Instant deletedAt;

    // Member와 동일하게 assigned String ID의 isNew 판별 문제를 해결하고(§3.1),
    // 여러 경로(수정/Worker 콜백/휴지통 이동)가 동시에 수정할 수 있는 애그리게잇이라 낙관적 락을 도입한다(§3.4).
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Transient
    private boolean isNew = false;

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
        artwork.id = UuidV7Generator.generate();
        artwork.authorId = authorId;
        artwork.title = title;
        artwork.description = description;
        artwork.attachImages(imageKeys, representativeImageIndex);
        artwork.thumbnailKey = thumbnailKey;
        artwork.imageLayoutType = imageLayoutType;
        artwork.artworkField = artworkField;
        artwork.creativeType = creativeType;
        artwork.roles = new HashSet<>(roles != null ? roles : List.of());
        artwork.genres = new HashSet<>(genres != null ? genres : List.of());
        artwork.tags = new HashSet<>(tags != null ? tags : List.of());
        artwork.ageRating = ageRating;
        artwork.visibility = visibility;
        artwork.tools = new HashSet<>(tools != null ? tools : List.of());
        artwork.setWorkDuration(workDuration);
        artwork.cutCount = cutCount;
        artwork.videoLinks = new ArrayList<>(videoLinks != null ? videoLinks : List.of());
        artwork.attachMaterials(materials);
        artwork.status = ArtworkStatus.PROCESSING;
        artwork.isNew = true;
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
                              List<String> videoLinks) {
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
        if (roles != null) this.roles = new HashSet<>(roles);
        if (genres != null) this.genres = new HashSet<>(genres);
        if (tags != null) this.tags = new HashSet<>(tags);
        if (ageRating != null) this.ageRating = ageRating;
        if (tools != null) this.tools = new HashSet<>(tools);
        if (workDuration != null) this.setWorkDuration(workDuration);
        if (cutCount != null) this.cutCount = cutCount;
        if (videoLinks != null) this.videoLinks = new ArrayList<>(videoLinks);
    }

    // 이미지 교체 1단계 — 기존 이미지를 컬렉션에서 제거해 orphanRemoval 삭제를 예약하고 원본 데이터를 반환한다.
    // 호출자(Service)는 이 메서드 뒤에 saveAndFlush로 삭제를 물리적으로 확정한 다음 attachImages를 호출해야 한다 —
    // 그렇지 않으면 Hibernate가 같은 flush에서 신규 이미지 INSERT를 기존 이미지 DELETE보다 먼저 실행해
    // uk_ai_order(artwork_id, ordinal) 유니크 제약과 충돌한다(§3.3.2 RefreshToken과 동일 계열의 함정, 신규 발견).
    public List<ArtworkImage> detachImages() {
        List<ArtworkImage> removed = new ArrayList<>(this.images);
        this.images.clear();
        return removed;
    }

    // 이미지 교체 2단계 — 신규 이미지를 ordinal 0부터 채우고 PROCESSING 상태로 전환한다.
    public void attachImages(List<String> newImageKeys, int newRepresentativeIndex) {
        if (newImageKeys == null || newImageKeys.isEmpty() || newImageKeys.size() > 20) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_IMAGE_COUNT);
        }
        if (newRepresentativeIndex < 0 || newRepresentativeIndex >= newImageKeys.size()) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_REPRESENTATIVE_INDEX);
        }
        for (int i = 0; i < newImageKeys.size(); i++) {
            this.images.add(ArtworkImage.pending(this, i, newImageKeys.get(i)));
        }
        this.representativeImageIndex = newRepresentativeIndex;
        this.status = ArtworkStatus.PROCESSING;
    }

    // 자재 교체 1단계 — detachImages와 동일한 이유로 삭제를 먼저 예약한다(uk_am_order 충돌 방지).
    public void detachMaterials() {
        this.materials.clear();
    }

    // 자재 교체 2단계 — ordinal 0부터 채워 붙인다.
    public void attachMaterials(List<Material> newMaterials) {
        List<Material> source = newMaterials != null ? newMaterials : List.of();
        for (int i = 0; i < source.size(); i++) {
            Material material = source.get(i);
            material.attachTo(this, i);
            this.materials.add(material);
        }
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

    private void setWorkDuration(WorkDuration workDuration) {
        if (workDuration == null) {
            this.workDurationMonths = null;
            this.workDurationDays = null;
            this.workDurationHours = null;
            this.workDurationMinutes = null;
            return;
        }
        this.workDurationMonths = workDuration.months();
        this.workDurationDays = workDuration.days();
        this.workDurationHours = workDuration.hours();
        this.workDurationMinutes = workDuration.minutes();
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
    public List<ArtworkRole> getRoles() { return roles.stream().sorted().toList(); }
    public List<String> getGenres() { return genres.stream().sorted().toList(); }
    public List<String> getTags() { return tags.stream().sorted().toList(); }
    public List<String> getTools() { return tools.stream().sorted().toList(); }

    public WorkDuration getWorkDuration() {
        if (workDurationMonths == null && workDurationDays == null
                && workDurationHours == null && workDurationMinutes == null) {
            return null;
        }
        return new WorkDuration(workDurationMonths, workDurationDays, workDurationHours, workDurationMinutes);
    }

    public Integer getCutCount() { return cutCount; }
    public List<String> getVideoLinks() { return videoLinks != null ? List.copyOf(videoLinks) : List.of(); }
    public AgeRating getAgeRating() { return ageRating; }
    public Visibility getVisibility() { return visibility; }
    public List<Material> getMaterials() { return List.copyOf(materials); }
    public ArtworkStatus getStatus() { return status; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
