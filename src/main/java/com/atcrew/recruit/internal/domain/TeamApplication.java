package com.atcrew.recruit.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.recruit.ApplicationReviewStatus;
import com.atcrew.recruit.CreateApplicationCommand;
import com.atcrew.recruit.SerialExperience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 팀원모집글 지원 (docs/design/recruit-module-design.md §2.4).
 * 요구 필드는 {@link JobApplication}과 같지만 지원 대상이 다른 별개 도메인이라 테이블을 분리한다.
 * 중복 지원 방지는 (team_posting_id, applicant_member_id) 유니크 제약이 담당한다(§10-3).
 */
@Entity
@Table(name = "team_applications")
public class TeamApplication {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "team_posting_id", length = 36, nullable = false, updatable = false)
    private String teamPostingId;

    @Column(name = "applicant_member_id", length = 36, nullable = false, updatable = false)
    private String applicantMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "serial_experience", length = 30, nullable = false)
    private SerialExperience serialExperience;

    @Column(name = "assistant_experience", nullable = false)
    private boolean assistantExperience;

    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    private ApplicationReviewStatus reviewStatus;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    protected TeamApplication() {
    }

    public static TeamApplication create(String teamPostingId, String applicantMemberId,
            CreateApplicationCommand command) {
        TeamApplication application = new TeamApplication();
        application.id = UuidV7Generator.generate();
        application.teamPostingId = teamPostingId;
        application.applicantMemberId = applicantMemberId;
        application.serialExperience = command.serialExperience();
        application.assistantExperience = command.assistantExperience();
        application.resumeUrl = command.resumeUrl();
        application.reviewStatus = ApplicationReviewStatus.RECEIVED;
        application.appliedAt = Instant.now();
        return application;
    }

    // 채용 단계 변경 — 호출 전 서비스 레이어에서 팀원모집글 작성자 소유권을 검증한다(§2.6).
    public void changeReviewStatus(ApplicationReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getId() { return id; }
    public String getTeamPostingId() { return teamPostingId; }
    public String getApplicantMemberId() { return applicantMemberId; }
    public SerialExperience getSerialExperience() { return serialExperience; }
    public boolean isAssistantExperience() { return assistantExperience; }
    public String getResumeUrl() { return resumeUrl; }
    public ApplicationReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getAppliedAt() { return appliedAt; }
}
