package com.atcrew.recruit.internal.domain;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.common.persistence.StringListJsonConverter;
import com.atcrew.recruit.CreateJobPostingCommand;
import com.atcrew.recruit.JobEmploymentType;
import com.atcrew.recruit.JobPaymentType;
import com.atcrew.recruit.JobPaymentUnit;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.JobWorkLocationType;
import com.atcrew.recruit.JobWorkScheduleType;
import com.atcrew.recruit.UpdateJobPostingCommand;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 구인글 (docs/design/recruit-module-design.md §2.1).
 */
@Entity
@Table(name = "job_postings")
@EntityListeners(AuditingEntityListener.class)
public class JobPosting {

    // 끌어올리기 적용 기간이자 재적용 쿨다운(설계 §2.1.1)
    private static final Duration BOOST_DURATION = Duration.ofHours(48);

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "author_member_id", length = 36, nullable = false)
    private String authorMemberId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "ceo_name", length = 100)
    private String ceoName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "contact", length = 100)
    private String contact;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;

    @Column(name = "is_business_registered", nullable = false)
    private boolean isBusinessRegistered;

    @Column(name = "is_resume_required", nullable = false)
    private boolean isResumeRequired;

    @Column(name = "is_cover_letter_required", nullable = false)
    private boolean isCoverLetterRequired;

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "job_posting_roles", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "role", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private List<ArtworkRole> roles = new ArrayList<>();

    @ElementCollection
    @BatchSize(size = 100)   // 목록 조회 시 컬렉션 지연 로딩 N+1 완화
    @CollectionTable(name = "job_posting_genres", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "genre", length = 30, nullable = false)
    @Enumerated(EnumType.STRING)
    private List<Genre> genres = new ArrayList<>();

    @Column(name = "work_scope", length = 500)
    private String workScope;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "recruit_count")
    private Integer recruitCount;

    @Column(name = "hiring_process", columnDefinition = "TEXT")
    private String hiringProcess;

    @Column(name = "education", length = 200)
    private String education;

    @Column(name = "experience", length = 200)
    private String experience;

    @Column(name = "age", length = 100)
    private String age;

    @Column(name = "gender", length = 50)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private JobEmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_location_type", length = 30)
    private JobWorkLocationType workLocationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_schedule_type", length = 30)
    private JobWorkScheduleType workScheduleType;

    @Column(name = "core_time_start")
    private LocalTime coreTimeStart;

    @Column(name = "core_time_end")
    private LocalTime coreTimeEnd;

    @Column(name = "has_overtime_pay", nullable = false)
    private boolean hasOvertimePay;

    @Column(name = "has_social_insurance", nullable = false)
    private boolean hasSocialInsurance;

    @Column(name = "has_contract", nullable = false)
    private boolean hasContract;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 30)
    private JobPaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_unit", length = 30)
    private JobPaymentUnit paymentUnit;

    @Column(name = "min_amount")
    private Long minAmount;

    @Column(name = "max_amount")
    private Long maxAmount;

    @Column(name = "is_negotiable", nullable = false)
    private boolean isNegotiable;

    @Column(name = "mg_amount")
    private Long mgAmount;

    @Column(name = "rs_ratio", precision = 5, scale = 2)
    private BigDecimal rsRatio;

    @Column(name = "has_buyout", nullable = false)
    private boolean hasBuyout;

    @Column(name = "benefit_description", columnDefinition = "TEXT")
    private String benefitDescription;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "benefit_keywords", columnDefinition = "JSON")
    private List<String> benefitKeywords = new ArrayList<>();

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
    private JobPostingStatus status;

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

    protected JobPosting() {
    }

    public static JobPosting create(String authorMemberId, CreateJobPostingCommand command) {
        JobPosting jobPosting = new JobPosting();
        jobPosting.id = UuidV7Generator.generate();
        jobPosting.authorMemberId = authorMemberId;
        jobPosting.title = command.title();
        jobPosting.companyName = command.companyName();
        jobPosting.ceoName = command.ceoName();
        jobPosting.industry = command.industry();
        jobPosting.address = command.address();
        jobPosting.contact = command.contact();
        jobPosting.websiteUrl = command.websiteUrl();
        jobPosting.companyDescription = command.companyDescription();
        jobPosting.isBusinessRegistered = command.isBusinessRegistered();
        jobPosting.isResumeRequired = command.isResumeRequired();
        jobPosting.isCoverLetterRequired = command.isCoverLetterRequired();
        jobPosting.roles = new ArrayList<>(command.roles() != null ? command.roles() : List.of());
        jobPosting.genres = new ArrayList<>(command.genres() != null ? command.genres() : List.of());
        jobPosting.workScope = command.workScope();
        jobPosting.deadline = command.deadline();
        jobPosting.recruitCount = command.recruitCount();
        jobPosting.hiringProcess = command.hiringProcess();
        jobPosting.education = command.education();
        jobPosting.experience = command.experience();
        jobPosting.age = command.age();
        jobPosting.gender = command.gender();
        jobPosting.employmentType = command.employmentType();
        jobPosting.workLocationType = command.workLocationType();
        jobPosting.workScheduleType = command.workScheduleType();
        jobPosting.coreTimeStart = command.coreTimeStart();
        jobPosting.coreTimeEnd = command.coreTimeEnd();
        jobPosting.hasOvertimePay = command.hasOvertimePay();
        jobPosting.hasSocialInsurance = command.hasSocialInsurance();
        jobPosting.hasContract = command.hasContract();
        jobPosting.paymentType = command.paymentType();
        jobPosting.paymentUnit = command.paymentUnit();
        jobPosting.minAmount = command.minAmount();
        jobPosting.maxAmount = command.maxAmount();
        jobPosting.isNegotiable = command.isNegotiable();
        jobPosting.mgAmount = command.mgAmount();
        jobPosting.rsRatio = command.rsRatio();
        jobPosting.hasBuyout = command.hasBuyout();
        jobPosting.benefitDescription = command.benefitDescription();
        jobPosting.benefitKeywords = new ArrayList<>(command.benefitKeywords() != null ? command.benefitKeywords() : List.of());
        jobPosting.thumbnailImage = command.thumbnailImage();
        jobPosting.referenceImages = new ArrayList<>(command.referenceImages() != null ? command.referenceImages() : List.of());
        jobPosting.bookmarkCount = 0L;
        jobPosting.viewCount = 0L;
        jobPosting.status = JobPostingStatus.DRAFT;
        // 이미지 등록 여부는 서비스가 media 모듈에 위임한 뒤 markImageProcessingPending()으로 표시한다.
        jobPosting.imageProcessingStatus = RecruitImageProcessingStatus.READY;
        jobPosting.enforceCoreTimeInvariant();
        jobPosting.validateAmountRange();
        return jobPosting;
    }

    // 부분 업데이트 — null 필드는 기존 값 유지. 휴지통(DELETED) 여부 검증은 서비스 레이어에서 수행한다.
    public void updateContent(UpdateJobPostingCommand command) {
        if (command.title() != null) this.title = command.title();
        if (command.companyName() != null) this.companyName = command.companyName();
        if (command.ceoName() != null) this.ceoName = command.ceoName();
        if (command.industry() != null) this.industry = command.industry();
        if (command.address() != null) this.address = command.address();
        if (command.contact() != null) this.contact = command.contact();
        if (command.websiteUrl() != null) this.websiteUrl = command.websiteUrl();
        if (command.companyDescription() != null) this.companyDescription = command.companyDescription();
        if (command.isBusinessRegistered() != null) this.isBusinessRegistered = command.isBusinessRegistered();
        if (command.isResumeRequired() != null) this.isResumeRequired = command.isResumeRequired();
        if (command.isCoverLetterRequired() != null) this.isCoverLetterRequired = command.isCoverLetterRequired();
        if (command.roles() != null) this.roles = new ArrayList<>(command.roles());
        if (command.genres() != null) this.genres = new ArrayList<>(command.genres());
        if (command.workScope() != null) this.workScope = command.workScope();
        if (command.deadline() != null) this.deadline = command.deadline();
        if (command.recruitCount() != null) this.recruitCount = command.recruitCount();
        if (command.hiringProcess() != null) this.hiringProcess = command.hiringProcess();
        if (command.education() != null) this.education = command.education();
        if (command.experience() != null) this.experience = command.experience();
        if (command.age() != null) this.age = command.age();
        if (command.gender() != null) this.gender = command.gender();
        if (command.employmentType() != null) this.employmentType = command.employmentType();
        if (command.workLocationType() != null) this.workLocationType = command.workLocationType();
        if (command.workScheduleType() != null) this.workScheduleType = command.workScheduleType();
        if (command.coreTimeStart() != null) this.coreTimeStart = command.coreTimeStart();
        if (command.coreTimeEnd() != null) this.coreTimeEnd = command.coreTimeEnd();
        if (command.hasOvertimePay() != null) this.hasOvertimePay = command.hasOvertimePay();
        if (command.hasSocialInsurance() != null) this.hasSocialInsurance = command.hasSocialInsurance();
        if (command.hasContract() != null) this.hasContract = command.hasContract();
        if (command.paymentType() != null) this.paymentType = command.paymentType();
        if (command.paymentUnit() != null) this.paymentUnit = command.paymentUnit();
        if (command.minAmount() != null) this.minAmount = command.minAmount();
        if (command.maxAmount() != null) this.maxAmount = command.maxAmount();
        if (command.isNegotiable() != null) this.isNegotiable = command.isNegotiable();
        if (command.mgAmount() != null) this.mgAmount = command.mgAmount();
        if (command.rsRatio() != null) this.rsRatio = command.rsRatio();
        if (command.hasBuyout() != null) this.hasBuyout = command.hasBuyout();
        if (command.benefitDescription() != null) this.benefitDescription = command.benefitDescription();
        if (command.benefitKeywords() != null) this.benefitKeywords = new ArrayList<>(command.benefitKeywords());
        if (command.thumbnailImage() != null) this.thumbnailImage = command.thumbnailImage();
        if (command.referenceImages() != null) this.referenceImages = new ArrayList<>(command.referenceImages());
        enforceCoreTimeInvariant();
        validateAmountRange();
    }

    // 코어타임은 자율 근무제(FLEXIBLE)일 때만 의미가 있다 — 그 외에는 강제로 비운다(도메인 불변식).
    private void enforceCoreTimeInvariant() {
        if (this.workScheduleType != JobWorkScheduleType.FLEXIBLE) {
            this.coreTimeStart = null;
            this.coreTimeEnd = null;
        }
    }

    private void validateAmountRange() {
        if (this.minAmount != null && this.maxAmount != null && this.minAmount > this.maxAmount) {
            throw new RecruitException(RecruitErrorCode.INVALID_AMOUNT_RANGE);
        }
    }

    // DRAFT/PENDING → PENDING (최초 제출 및 재제출)
    public void submitForApproval() {
        if (status != JobPostingStatus.DRAFT && status != JobPostingStatus.PENDING) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION,
                    "submitForApproval 불가 상태: " + status);
        }
        this.status = JobPostingStatus.PENDING;
    }

    // PENDING → PUBLISHED (관리자 승인)
    public void approve() {
        if (status != JobPostingStatus.PENDING) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "approve 불가 상태: " + status);
        }
        this.status = JobPostingStatus.PUBLISHED;
    }

    // PENDING → CLOSED (관리자 반려)
    public void reject() {
        if (status != JobPostingStatus.PENDING) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "reject 불가 상태: " + status);
        }
        this.status = JobPostingStatus.CLOSED;
    }

    // PUBLISHED/PENDING → CLOSED (작성자 마감)
    public void close() {
        if (status != JobPostingStatus.PUBLISHED && status != JobPostingStatus.PENDING) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "close 불가 상태: " + status);
        }
        this.status = JobPostingStatus.CLOSED;
    }

    // 모든 상태 → DELETED (휴지통 이동, soft delete)
    public void moveToTrash() {
        if (status == JobPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "이미 휴지통에 있는 구인글입니다");
        }
        this.status = JobPostingStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    // DELETED → DRAFT (휴지통 복구)
    public void restore() {
        if (status != JobPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "휴지통에 있는 구인글이 아닙니다");
        }
        this.status = JobPostingStatus.DRAFT;
        this.deletedAt = null;
    }

    /**
     * 끌어올리기 적용 — {@code boostedUntil = now + 48h}로 갱신한다(설계 §2.1.1).
     * 적용 기간과 쿨다운이 같아 아직 적용 중이면(now < boostedUntil) 재적용을 거부한다.
     */
    public void boost(Instant now) {
        if (status == JobPostingStatus.DELETED) {
            throw new RecruitException(RecruitErrorCode.INVALID_STATUS_TRANSITION, "휴지통에 있는 구인글은 끌어올릴 수 없습니다");
        }
        if (boostedUntil != null && now.isBefore(boostedUntil)) {
            throw new RecruitException(RecruitErrorCode.BOOST_COOLDOWN, "boostedUntil=" + boostedUntil);
        }
        this.boostedUntil = now.plus(BOOST_DURATION);
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
    public String getCompanyName() { return companyName; }
    public String getCeoName() { return ceoName; }
    public String getIndustry() { return industry; }
    public String getAddress() { return address; }
    public String getContact() { return contact; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getCompanyDescription() { return companyDescription; }
    public boolean isBusinessRegistered() { return isBusinessRegistered; }
    public boolean isResumeRequired() { return isResumeRequired; }
    public boolean isCoverLetterRequired() { return isCoverLetterRequired; }
    public List<ArtworkRole> getRoles() { return List.copyOf(roles); }
    public List<Genre> getGenres() { return List.copyOf(genres); }
    public String getWorkScope() { return workScope; }
    public LocalDate getDeadline() { return deadline; }
    public Integer getRecruitCount() { return recruitCount; }
    public String getHiringProcess() { return hiringProcess; }
    public String getEducation() { return education; }
    public String getExperience() { return experience; }
    public String getAge() { return age; }
    public String getGender() { return gender; }
    public JobEmploymentType getEmploymentType() { return employmentType; }
    public JobWorkLocationType getWorkLocationType() { return workLocationType; }
    public JobWorkScheduleType getWorkScheduleType() { return workScheduleType; }
    public LocalTime getCoreTimeStart() { return coreTimeStart; }
    public LocalTime getCoreTimeEnd() { return coreTimeEnd; }
    public boolean isHasOvertimePay() { return hasOvertimePay; }
    public boolean isHasSocialInsurance() { return hasSocialInsurance; }
    public boolean isHasContract() { return hasContract; }
    public JobPaymentType getPaymentType() { return paymentType; }
    public JobPaymentUnit getPaymentUnit() { return paymentUnit; }
    public Long getMinAmount() { return minAmount; }
    public Long getMaxAmount() { return maxAmount; }
    public boolean isNegotiable() { return isNegotiable; }
    public Long getMgAmount() { return mgAmount; }
    public BigDecimal getRsRatio() { return rsRatio; }
    public boolean isHasBuyout() { return hasBuyout; }
    public String getBenefitDescription() { return benefitDescription; }
    public List<String> getBenefitKeywords() { return List.copyOf(benefitKeywords); }
    public String getThumbnailImage() { return thumbnailImage; }
    public List<String> getReferenceImages() { return List.copyOf(referenceImages); }
    public long getBookmarkCount() { return bookmarkCount; }
    public long getViewCount() { return viewCount; }
    public Instant getBoostedUntil() { return boostedUntil; }
    public JobPostingStatus getStatus() { return status; }
    public RecruitImageProcessingStatus getImageProcessingStatus() { return imageProcessingStatus; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
