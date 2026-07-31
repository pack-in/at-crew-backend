package com.atcrew.recruit.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.common.persistence.StringListJsonConverter;
import com.atcrew.recruit.CreateTeamPostingCommand;
import com.atcrew.recruit.TeamActivityDuration;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.TeamWeeklyActivityTime;
import com.atcrew.recruit.TeamWorkLocationType;
import com.atcrew.recruit.UpdateTeamPostingCommand;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 팀원모집글 (docs/design/recruit-module-design.md §2.2).
 * JobPosting과 달리 승인 절차가 없다 — 생성 시 즉시 PUBLISHED, 부스트도 없다(§0).
 */
@Entity
@Table(name = "team_postings")
@EntityListeners(AuditingEntityListener.class)
public class TeamPosting {

    // 끌어올리기 적용 기간이자 재적용 쿨다운(설계 §2.1.1)
    private static final Duration BOOST_DURATION = Duration.ofHours(48);

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "author_member_id", length = 36, nullable = false)
    private String authorMemberId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "is_business_registered", nullable = false)
    private boolean isBusinessRegistered;

    @Column(name = "is_resume_required", nullable = false)
    private boolean isResumeRequired;

    @Column(name = "is_cover_letter_required", nullable = false)
    private boolean isCoverLetterRequired;

    // 모집자(팀/개인)가 폼에 직접 입력하는 표시명 — Member 조회로 얻는 표시명(TeamPostingInfo.authorDisplayName)과는 별개
    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "contact", length = 100)
    private String contact;

    @Column(name = "author_description", columnDefinition = "TEXT")
    private String authorDescription;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "recruit_purposes", columnDefinition = "JSON")
    private List<String> recruitPurposes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "work_location_type", length = 30)
    private TeamWorkLocationType workLocationType;

    // workLocationType=ONLINE이면 반드시 null이어야 한다(도메인 불변식, 설계 §2.2)
    @Column(name = "activity_region", length = 200)
    private String activityRegion;

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "team_posting_roles", joinColumns = @JoinColumn(name = "team_posting_id"))
    @Column(name = "role", length = 100, nullable = false)
    private List<String> roles = new ArrayList<>();

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "team_posting_genres", joinColumns = @JoinColumn(name = "team_posting_id"))
    @Column(name = "genre", length = 100, nullable = false)
    private List<String> genres = new ArrayList<>();

    @Column(name = "has_participation_fee", nullable = false)
    private boolean hasParticipationFee;

    @Column(name = "has_profit_sharing", nullable = false)
    private boolean hasProfitSharing;

    @Column(name = "extra_cost", length = 500)
    private String extraCost;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "recruit_count")
    private Integer recruitCount;

    @Column(name = "selection_process", columnDefinition = "TEXT")
    private String selectionProcess;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_duration", length = 30)
    private TeamActivityDuration activityDuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_activity_time", length = 30)
    private TeamWeeklyActivityTime weeklyActivityTime;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "thumbnail_image", length = 500)
    private String thumbnailImage;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "reference_images", columnDefinition = "JSON")
    private List<String> referenceImages = new ArrayList<>();

    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "boosted_until")
    private Instant boostedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TeamPostingStatus status;

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

    protected TeamPosting() {
    }

    // 승인 절차가 없으므로 생성 즉시 PUBLISHED로 저장한다(설계 §2.2, §0).
    public static TeamPosting create(String authorMemberId, CreateTeamPostingCommand command) {
        TeamPosting teamPosting = new TeamPosting();
        teamPosting.id = UuidV7Generator.generate();
        teamPosting.authorMemberId = authorMemberId;
        teamPosting.title = command.title();
        teamPosting.isBusinessRegistered = command.isBusinessRegistered();
        teamPosting.isResumeRequired = command.isResumeRequired();
        teamPosting.isCoverLetterRequired = command.isCoverLetterRequired();
        teamPosting.authorName = command.authorName();
        teamPosting.contact = command.contact();
        teamPosting.authorDescription = command.authorDescription();
        teamPosting.recruitPurposes = new ArrayList<>(command.recruitPurposes() != null ? command.recruitPurposes() : List.of());
        teamPosting.workLocationType = command.workLocationType();
        teamPosting.activityRegion = command.activityRegion();
        teamPosting.roles = new ArrayList<>(command.roles() != null ? command.roles() : List.of());
        teamPosting.genres = new ArrayList<>(command.genres() != null ? command.genres() : List.of());
        teamPosting.hasParticipationFee = command.hasParticipationFee();
        teamPosting.hasProfitSharing = command.hasProfitSharing();
        teamPosting.extraCost = command.extraCost();
        teamPosting.deadline = command.deadline();
        teamPosting.recruitCount = command.recruitCount();
        teamPosting.selectionProcess = command.selectionProcess();
        teamPosting.activityDuration = command.activityDuration();
        teamPosting.weeklyActivityTime = command.weeklyActivityTime();
        teamPosting.projectDescription = command.projectDescription();
        teamPosting.thumbnailImage = command.thumbnailImage();
        teamPosting.referenceImages = new ArrayList<>(command.referenceImages() != null ? command.referenceImages() : List.of());
        teamPosting.bookmarkCount = 0L;
        teamPosting.viewCount = 0L;
        teamPosting.status = TeamPostingStatus.PUBLISHED;
        teamPosting.validateActivityRegionInvariant();
        return teamPosting;
    }

    // 부분 업데이트 — null 필드는 기존 값 유지. 휴지통(DELETED) 여부 검증은 서비스 레이어에서 수행한다.
    public void updateContent(UpdateTeamPostingCommand command) {
        if (command.title() != null) this.title = command.title();
        if (command.isBusinessRegistered() != null) this.isBusinessRegistered = command.isBusinessRegistered();
        if (command.isResumeRequired() != null) this.isResumeRequired = command.isResumeRequired();
        if (command.isCoverLetterRequired() != null) this.isCoverLetterRequired = command.isCoverLetterRequired();
        if (command.authorName() != null) this.authorName = command.authorName();
        if (command.contact() != null) this.contact = command.contact();
        if (command.authorDescription() != null) this.authorDescription = command.authorDescription();
        if (command.recruitPurposes() != null) this.recruitPurposes = new ArrayList<>(command.recruitPurposes());
        if (command.workLocationType() != null) this.workLocationType = command.workLocationType();
        if (command.activityRegion() != null) this.activityRegion = command.activityRegion();
        if (command.roles() != null) this.roles = new ArrayList<>(command.roles());
        if (command.genres() != null) this.genres = new ArrayList<>(command.genres());
        if (command.hasParticipationFee() != null) this.hasParticipationFee = command.hasParticipationFee();
        if (command.hasProfitSharing() != null) this.hasProfitSharing = command.hasProfitSharing();
        if (command.extraCost() != null) this.extraCost = command.extraCost();
        if (command.deadline() != null) this.deadline = command.deadline();
        if (command.recruitCount() != null) this.recruitCount = command.recruitCount();
        if (command.selectionProcess() != null) this.selectionProcess = command.selectionProcess();
        if (command.activityDuration() != null) this.activityDuration = command.activityDuration();
        if (command.weeklyActivityTime() != null) this.weeklyActivityTime = command.weeklyActivityTime();
        if (command.projectDescription() != null) this.projectDescription = command.projectDescription();
        if (command.thumbnailImage() != null) this.thumbnailImage = command.thumbnailImage();
        if (command.referenceImages() != null) this.referenceImages = new ArrayList<>(command.referenceImages());
        validateActivityRegionInvariant();
    }

    // workLocationType=ONLINE이면 activityRegion은 반드시 null이어야 한다(설계 §2.2 도메인 불변식)
    private void validateActivityRegionInvariant() {
        if (this.workLocationType == TeamWorkLocationType.ONLINE && this.activityRegion != null) {
            throw new RecruitException(RecruitErrorCode.INVALID_ACTIVITY_REGION);
        }
    }

    // PUBLISHED → CLOSED (작성자 마감). Team은 PENDING이 없어 Job과 달리 PUBLISHED에서만 전이 가능하다.
    public void close() {
        if (status != TeamPostingStatus.PUBLISHED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "close 불가 상태: " + status);
        }
        this.status = TeamPostingStatus.CLOSED;
    }

    // 모든 상태 → DELETED (휴지통 이동, soft delete)
    public void moveToTrash() {
        if (status == TeamPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "이미 휴지통에 있는 팀원모집글입니다");
        }
        this.status = TeamPostingStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // DELETED → PUBLISHED (휴지통 복구). 승인 절차가 없으므로 Job과 달리 DRAFT를 거치지 않고 즉시 재게시한다.
    public void restore() {
        if (status != TeamPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "휴지통에 있는 팀원모집글이 아닙니다");
        }
        this.status = TeamPostingStatus.PUBLISHED;
        this.deletedAt = null;
    }

    /**
     * 끌어올리기 적용 — 규칙은 JobPosting과 동일하다(설계 §2.1.1).
     */
    public void boost(Instant now) {
        if (status == TeamPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "휴지통에 있는 팀원모집글은 끌어올릴 수 없습니다");
        }
        if (boostedUntil != null && now.isBefore(boostedUntil)) {
            throw new RecruitException(RecruitErrorCode.BOOST_COOLDOWN, "boostedUntil=" + boostedUntil);
        }
        this.boostedUntil = now.plus(BOOST_DURATION);
    }

    public void checkAuthor(String memberId) {
        if (!this.authorMemberId.equals(memberId)) {
            throw new RecruitException(RecruitErrorCode.FORBIDDEN_NOT_AUTHOR);
        }
    }

    public String getId() { return id; }
    public String getAuthorMemberId() { return authorMemberId; }
    public String getTitle() { return title; }
    public boolean isBusinessRegistered() { return isBusinessRegistered; }
    public boolean isResumeRequired() { return isResumeRequired; }
    public boolean isCoverLetterRequired() { return isCoverLetterRequired; }
    public String getAuthorName() { return authorName; }
    public String getContact() { return contact; }
    public String getAuthorDescription() { return authorDescription; }
    public List<String> getRecruitPurposes() { return List.copyOf(recruitPurposes); }
    public TeamWorkLocationType getWorkLocationType() { return workLocationType; }
    public String getActivityRegion() { return activityRegion; }
    public List<String> getRoles() { return List.copyOf(roles); }
    public List<String> getGenres() { return List.copyOf(genres); }
    public boolean isHasParticipationFee() { return hasParticipationFee; }
    public boolean isHasProfitSharing() { return hasProfitSharing; }
    public String getExtraCost() { return extraCost; }
    public LocalDate getDeadline() { return deadline; }
    public Integer getRecruitCount() { return recruitCount; }
    public String getSelectionProcess() { return selectionProcess; }
    public TeamActivityDuration getActivityDuration() { return activityDuration; }
    public TeamWeeklyActivityTime getWeeklyActivityTime() { return weeklyActivityTime; }
    public String getProjectDescription() { return projectDescription; }
    public String getThumbnailImage() { return thumbnailImage; }
    public List<String> getReferenceImages() { return List.copyOf(referenceImages); }
    public long getBookmarkCount() { return bookmarkCount; }
    public long getViewCount() { return viewCount; }
    public Instant getBoostedUntil() { return boostedUntil; }
    public TeamPostingStatus getStatus() { return status; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
