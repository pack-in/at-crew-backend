package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkAccess;
import com.atcrew.artwork.ArtworkCustomTagInfo;
import com.atcrew.artwork.ArtworkCustomTagType;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.Genre;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.WorkDuration;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.member.Language;
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

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "artworks")
@EntityListeners(AuditingEntityListener.class)
// viewCount/bookmarkCount는 ArtworkRepository의 벌크 UPDATE로만 갱신된다(이슈 #78). 이 엔티티가 여느
// 저장 경로(수정·복구·Worker 콜백 등)에서 전체 컬럼 UPDATE를 내면 로드 시점의 예전 카운터 값으로
// 그 벌크 UPDATE 결과를 덮어써 되돌린다 — dirty 필드만 SET하도록 강제해 이를 막는다.
@DynamicUpdate
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
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
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
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
    @CollectionTable(name = "artwork_roles", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<ArtworkRole> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
    @CollectionTable(name = "artwork_genres", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<Genre> genres = new HashSet<>();

    // 직접입력 태그 — 항목(type)을 함께 저장해 한 테이블로 관리한다(ArtworkCustomTagType 참고,
    // com.atcrew.member.internal.domain.CustomTag와 동일 패턴).
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
    @CollectionTable(name = "artwork_custom_tags", joinColumns = @JoinColumn(name = "artwork_id"))
    private Set<ArtworkCustomTag> customTags = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
    @CollectionTable(name = "artwork_tags", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    private Set<String> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
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

    // 커뮤니티 피드 조회순 정렬용 누적 조회수(이슈 #78). 본인 조회는 제외하고 상세 조회마다 단순 증가하며,
    // 증가는 엔티티가 아니라 ArtworkRepository의 원자적 UPDATE가 담당한다 — 동시 조회가 서로의 증가를
    // 덮어쓰지 않게 하기 위함이다(recruit JobPosting.viewCount와 동일 방식).
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    // 커뮤니티 피드 북마크순 정렬용 북마크 수(이슈 #78). bookmark_entries의 COUNT를 비정규화한 값이며
    // 저장/삭제 시 원자적 UPDATE로 증감한다.
    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    // 작품 설정 6단계의 "게시물 작성·노출 언어"(업로드-R30). 스타터는 주 사용 언어 1개, 프로는 다중 선택이며
    // 개수 검증은 플랜을 아는 서비스 계층이 한다. 마이그레이션 이전 작품은 비어 있고, 언어 필터에서
    // "항상 노출"로 폴백한다.
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
    @CollectionTable(name = "artwork_languages", joinColumns = @JoinColumn(name = "artwork_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<Language> languages = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    // 라이브 포트폴리오(작가 페이지 + 최신 반영형) 편입 여부 — portfolio 모듈이 같은 트랜잭션에서 동기 갱신하는
    // 비정규화 값이다. artwork가 portfolio를 참조하면 순환 의존이 되므로 이 컬럼만 보고 접근을 판정한다
    // (docs/design/portfolio-module-design.md §1.2).
    @Column(name = "portfolio_included")
    private boolean portfolioIncluded;

    // 운영 정책·법적 조치에 따른 외부 노출 중단 시각(마이페이지_작가-R39). 사용자 삭제(status=DELETED)와
    // 구분되는 별도 축이며, 관리자 API가 없는 현재는 DB 직접 UPDATE로만 설정된다
    // (docs/operations/moderation-block.md).
    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Enumerated(EnumType.STRING)
    private Visibility visibilityBeforeDelete;

    // images와 동일한 이유로 양방향 매핑 (§11/§12 패턴)
    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 N+1 완화 (recruit 모듈과 동일 패턴, 이슈 #112)
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
                                 List<Genre> genres, List<ArtworkCustomTagInfo> customTags,
                                 List<String> tags,
                                 AgeRating ageRating, List<Language> languages, Visibility visibility,
                                 List<String> tools, WorkDuration workDuration,
                                 Integer cutCount, List<String> videoLinks,
                                 List<Material> materials) {
        if (imageKeys == null || imageKeys.isEmpty() || imageKeys.size() > 30) {
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
        artwork.customTags = normalizeCustomTags(customTags != null ? customTags : List.of());
        artwork.tags = new HashSet<>(tags != null ? tags : List.of());
        artwork.ageRating = ageRating;
        artwork.languages = new HashSet<>(languages != null ? languages : List.of());
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

    public void assertDeleted() {
        if (status != ArtworkStatus.DELETED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_DELETED);
        }
    }

    public void updateDetails(String title, String description,
                              ImageLayoutType imageLayoutType, Integer representativeImageIndex,
                              String thumbnailKey,
                              ArtworkField artworkField, CreativeType creativeType,
                              List<ArtworkRole> roles, List<Genre> genres,
                              List<ArtworkCustomTagInfo> customTags, List<String> tags,
                              AgeRating ageRating, List<Language> languages, List<String> tools,
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
        if (customTags != null) this.customTags = normalizeCustomTags(customTags);
        if (tags != null) this.tags = new HashSet<>(tags);
        if (ageRating != null) this.ageRating = ageRating;
        if (languages != null) this.languages = new HashSet<>(languages);
        if (tools != null) this.tools = new HashSet<>(tools);
        if (workDuration != null) this.setWorkDuration(workDuration);
        if (cutCount != null) this.cutCount = cutCount;
        if (videoLinks != null) this.videoLinks = new ArrayList<>(videoLinks);
    }

    private static final int MAX_CUSTOM_TAGS_PER_TYPE = 10;

    /**
     * 직접입력 태그 정규화 (기획서 업로드-R13): 앞뒤 공백 제거, 공백만 남으면 저장하지 않음,
     * 최대 10자, 같은 항목 안에서 중복 불가. 항목당 개수 상한은 명세에 없지만, 무제한 저장을
     * 막기 위해 10개로 둔다(com.atcrew.member.internal.domain.Member.normalizeCustomTags와 동일 규칙).
     */
    private static Set<ArtworkCustomTag> normalizeCustomTags(List<ArtworkCustomTagInfo> tags) {
        Set<ArtworkCustomTag> normalized = new HashSet<>();
        Map<ArtworkCustomTagType, Integer> counts = new EnumMap<>(ArtworkCustomTagType.class);
        for (ArtworkCustomTagInfo tag : tags) {
            if (tag == null || tag.type() == null || tag.value() == null) continue;
            String value = tag.value().trim();
            if (value.isEmpty()) continue; // 공백만 입력 시 미저장
            if (value.length() > ArtworkCustomTag.MAX_LENGTH) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_CUSTOM_TAG,
                        "최대 " + ArtworkCustomTag.MAX_LENGTH + "자: " + value);
            }
            if (!normalized.add(new ArtworkCustomTag(tag.type(), value))) continue; // 중복은 조용히 무시
            int count = counts.merge(tag.type(), 1, Integer::sum);
            if (count > MAX_CUSTOM_TAGS_PER_TYPE) {
                throw new ArtworkException(ArtworkErrorCode.INVALID_CUSTOM_TAG,
                        tag.type() + " 항목당 최대 " + MAX_CUSTOM_TAGS_PER_TYPE + "개");
            }
        }
        return normalized;
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
        if (newImageKeys == null || newImageKeys.isEmpty() || newImageKeys.size() > 30) {
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

    /**
     * 노출 위치 재선언(업로드-R09)에 따른 공개 상태 변경.
     *
     * <p>이미지 처리 중(PROCESSING)에도 허용한다 — 업로드 시점에는 같은 조합(피드 공개 여부 ×
     * 포트폴리오)을 PROCESSING 상태에서도 그대로 받는데, 업로드 직후의 정정만 막으면 사용자가 처리
     * 완료까지 기다려야 한다. PROCESSING 작품은 공개 상태와 무관하게 피드·공유 목록·상세 어디에도
     * 노출되지 않으므로(status 필터, {@link #accessFor}) 이 값은 처리 완료 시 적용될 의도일 뿐이다.
     *
     * <p>휴지통 작품은 복원이 먼저다 — 노출 위치를 바꿀 수 없다.
     */
    public void changeVisibility(Visibility visibility) {
        if (status == ArtworkStatus.DELETED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_DELETED);
        }
        this.visibility = visibility;
    }

    // 탈퇴 이벤트 처리용 — READY 상태 체크 없이 강제 비공개.
    // 라이브 포트폴리오 편입 여부도 함께 해제한다 — 피드 공개만 끄면 accessFor가 편입을 근거로 여전히
    // ALLOWED를 돌려줘(§5.4의 2요소 판정) 탈퇴 회원의 작품이 제3자에게 계속 열린다.
    public void forcePrivate() {
        if (this.visibility != Visibility.PRIVATE) {
            this.visibility = Visibility.PRIVATE;
        }
        this.portfolioIncluded = false;
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

    // 뷰어별 접근 판정 — 공개 여부는 "피드 공개 여부 × 라이브 포트폴리오 편입 여부" 2요소로 계산한다
    // (마이페이지_작가-R04, docs/design/portfolio-module-design.md §5.4).
    // 피드 공개가 아니면서 라이브 포트폴리오에도 없는 작품이 곧 완전 비공개이며, 레거시 LINK_ONLY는
    // 이 계산에서 PRIVATE와 동일하게 취급한다("링크 공개"라는 제3의 상태를 인정하지 않는다).
    // 운영 차단은 삭제·공개 상태·고정형 설정보다 우선한다(R39) — 단 작성자 본인의 열람은 막지 않는다
    // (차단 안내 배지는 프론트가 ArtworkInfo.blocked로 그린다).
    public ArtworkAccess accessFor(String viewerMemberId) {
        boolean owner = authorId.equals(viewerMemberId);
        if (blockedAt != null) return owner ? ArtworkAccess.ALLOWED : ArtworkAccess.BLOCKED;
        if (status == ArtworkStatus.DELETED) return owner ? ArtworkAccess.ALLOWED : ArtworkAccess.DELETED;
        if (owner) return ArtworkAccess.ALLOWED;                              // PROCESSING도 본인은 열람
        if (status != ArtworkStatus.READY) return ArtworkAccess.NOT_FOUND;
        if (visibility == Visibility.PUBLIC) return ArtworkAccess.ALLOWED;
        return portfolioIncluded ? ArtworkAccess.ALLOWED : ArtworkAccess.PRIVATE;
    }

    // 포트폴리오 편입/제외 반영 — 호출 주체는 항상 portfolio 모듈이다(ArtworkService.updatePortfolioInclusion).
    public void updatePortfolioInclusion(boolean included) {
        this.portfolioIncluded = included;
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
    public List<Genre> getGenres() { return genres.stream().sorted().toList(); }

    public List<ArtworkCustomTagInfo> getCustomTags() {
        return customTags.stream()
                .sorted(java.util.Comparator.comparing(ArtworkCustomTag::getType).thenComparing(ArtworkCustomTag::getValue))
                .map(t -> new ArtworkCustomTagInfo(t.getType(), t.getValue()))
                .toList();
    }

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
    public long getViewCount() { return viewCount; }
    public long getBookmarkCount() { return bookmarkCount; }
    public List<Language> getLanguages() { return languages.stream().sorted().toList(); }
    public Visibility getVisibility() { return visibility; }
    public boolean isPortfolioIncluded() { return portfolioIncluded; }
    public boolean isBlocked() { return blockedAt != null; }
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
