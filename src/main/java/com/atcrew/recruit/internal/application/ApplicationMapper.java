package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.ApplicationInfo;
import com.atcrew.recruit.internal.domain.JobApplication;
import com.atcrew.recruit.internal.domain.TeamApplication;

class ApplicationMapper {

    private ApplicationMapper() {
    }

    static ApplicationInfo toInfo(JobApplication application, String applicantName) {
        return new ApplicationInfo(
                application.getId(),
                application.getJobPostingId(),
                application.getApplicantMemberId(),
                applicantName,
                application.getSerialExperience(),
                application.isAssistantExperience(),
                application.getResumeUrl(),
                application.getReviewStatus(),
                application.getAppliedAt()
        );
    }

    static ApplicationInfo toInfo(TeamApplication application, String applicantName) {
        return new ApplicationInfo(
                application.getId(),
                application.getTeamPostingId(),
                application.getApplicantMemberId(),
                applicantName,
                application.getSerialExperience(),
                application.isAssistantExperience(),
                application.getResumeUrl(),
                application.getReviewStatus(),
                application.getAppliedAt()
        );
    }
}
