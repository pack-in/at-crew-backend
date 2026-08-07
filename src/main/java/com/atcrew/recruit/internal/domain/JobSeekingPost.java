package com.atcrew.recruit.internal.domain;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.common.persistence.StringListJsonConverter;
import com.atcrew.recruit.CreateJobSeekingPostCommand;
import com.atcrew.recruit.FeedbackStyle;
import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.UpdateJobSeekingPostCommand;
import com.atcrew.recruit.WorkStyle;
import com.atcrew.recruit.internal.exception.RecruitErrorCode;
import com.atcrew.recruit.internal.exception.RecruitException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 구직글 (docs/design/recruit-module-design.md §2.3). 관리자 승인 절차가 없다는 점만 다르고
 * 나머지 라이프사이클(임시저장·마감·휴지통·복구)은 JobPosting과 대칭 구조다.
 */
@Entity
@Table(name = "job_seeking_posts")
@EntityListeners(AuditingEntityListener.class)
public class JobSeekingPost {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "author_member_id", length = 36, nullable = false)
    private String authorMemberId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "job_seeking_post_roles", joinColumns = @JoinColumn(name = "job_seeking_post_id"))
    @Column(name = "role", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private List<ArtworkRole> roles = new ArrayList<>();

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "job_seeking_post_genres", joinColumns = @JoinColumn(name = "job_seeking_post_id"))
    @Column(name = "genre", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private List<Genre> genres = new ArrayList<>();

    @Column(name = "drawing_style", length = 200)
    private String drawingStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_feedback_style", length = 30)
    private FeedbackStyle preferredFeedbackStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style", length = 30)
    private WorkStyle workStyle;

    @Column(name = "desired_rate", length = 200)
    private String desiredRate;

    @Column(name = "portfolio_description", columnDefinition = "TEXT")
    private String portfolioDescription;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "reference_images", columnDefinition = "JSON")
    private List<String> referenceImages = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private JobSeekingPostStatus status;

    // 발행 상태(status)와 독립된 이미지 처리 축 (설계 §10.2) — 둘을 합치지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "image_processing_status", length = 20, nullable = false)
    private RecruitImageProcessingStatus imageProcessingStatus;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobSeekingPost() {
    }

    public static JobSeekingPost create(String authorMemberId, CreateJobSeekingPostCommand command) {
        JobSeekingPost post = new JobSeekingPost();
        post.id = UuidV7Generator.generate();
        post.authorMemberId = authorMemberId;
        post.title = command.title();
        post.roles = new ArrayList<>(command.roles() != null ? command.roles() : List.of());
        post.genres = new ArrayList<>(command.genres() != null ? command.genres() : List.of());
        post.drawingStyle = command.drawingStyle();
        post.preferredFeedbackStyle = command.preferredFeedbackStyle();
        post.workStyle = command.workStyle();
        post.desiredRate = command.desiredRate();
        post.portfolioDescription = command.portfolioDescription();
        post.referenceImages = new ArrayList<>(command.referenceImages() != null ? command.referenceImages() : List.of());
        post.status = JobSeekingPostStatus.DRAFT;
        // 이미지 등록 여부는 서비스가 media 모듈에 위임한 뒤 markImageProcessingPending()으로 표시한다.
        post.imageProcessingStatus = RecruitImageProcessingStatus.READY;
        return post;
    }

    // 부분 업데이트 — null 필드는 기존 값 유지. 휴지통(DELETED) 여부 검증은 서비스 레이어에서 수행한다.
    public void updateContent(UpdateJobSeekingPostCommand command) {
        if (command.title() != null) this.title = command.title();
        if (command.roles() != null) this.roles = new ArrayList<>(command.roles());
        if (command.genres() != null) this.genres = new ArrayList<>(command.genres());
        if (command.drawingStyle() != null) this.drawingStyle = command.drawingStyle();
        if (command.preferredFeedbackStyle() != null) this.preferredFeedbackStyle = command.preferredFeedbackStyle();
        if (command.workStyle() != null) this.workStyle = command.workStyle();
        if (command.desiredRate() != null) this.desiredRate = command.desiredRate();
        if (command.portfolioDescription() != null) this.portfolioDescription = command.portfolioDescription();
        if (command.referenceImages() != null) this.referenceImages = new ArrayList<>(command.referenceImages());
    }

    // DRAFT/CLOSED → PUBLISHED (게시 및 재게시). 승인 절차가 없으므로 즉시 공개된다.
    public void publish() {
        if (status != JobSeekingPostStatus.DRAFT && status != JobSeekingPostStatus.CLOSED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "publish 불가 상태: " + status);
        }
        this.status = JobSeekingPostStatus.PUBLISHED;
    }

    // PUBLISHED → CLOSED (작성자 마감)
    public void close() {
        if (status != JobSeekingPostStatus.PUBLISHED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "close 불가 상태: " + status);
        }
        this.status = JobSeekingPostStatus.CLOSED;
    }

    // 모든 상태 → DELETED (휴지통 이동, soft delete)
    public void moveToTrash() {
        if (status == JobSeekingPostStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "이미 휴지통에 있는 구직글입니다");
        }
        this.status = JobSeekingPostStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // DELETED → DRAFT (휴지통 복구). 임시저장 상태가 있는 JobPosting과 동일하게 재게시 전 DRAFT를 거친다.
    public void restore() {
        if (status != JobSeekingPostStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "휴지통에 있는 구직글이 아닙니다");
        }
        this.status = JobSeekingPostStatus.DRAFT;
        this.deletedAt = null;
    }

    /** 이미지를 media 모듈에 새로 등록해 Worker 처리를 기다리는 상태로 표시한다(설계 §10.2). */
    public void markImageProcessingPending() {
        this.imageProcessingStatus = RecruitImageProcessingStatus.PENDING;
    }

    /** 처리할 이미지가 없거나 {@link RecruitPostingImage#readyFor} 조건을 만족했을 때 호출한다. */
    public void markImageProcessingReady() {
        this.imageProcessingStatus = RecruitImageProcessingStatus.READY;
    }

    public void checkAuthor(String memberId) {
        if (!this.authorMemberId.equals(memberId)) {
            throw new RecruitException(RecruitErrorCode.FORBIDDEN_NOT_AUTHOR);
        }
    }

    public String getId() { return id; }
    public String getAuthorMemberId() { return authorMemberId; }
    public String getTitle() { return title; }
    public List<ArtworkRole> getRoles() { return List.copyOf(roles); }
    public List<Genre> getGenres() { return List.copyOf(genres); }
    public String getDrawingStyle() { return drawingStyle; }
    public FeedbackStyle getPreferredFeedbackStyle() { return preferredFeedbackStyle; }
    public WorkStyle getWorkStyle() { return workStyle; }
    public String getDesiredRate() { return desiredRate; }
    public String getPortfolioDescription() { return portfolioDescription; }
    public List<String> getReferenceImages() { return List.copyOf(referenceImages); }
    public JobSeekingPostStatus getStatus() { return status; }
    public RecruitImageProcessingStatus getImageProcessingStatus() { return imageProcessingStatus; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
