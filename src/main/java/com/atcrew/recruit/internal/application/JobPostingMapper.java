package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.CommunityJobPostingCardInfo;
import com.atcrew.recruit.JobPostingInfo;
import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.internal.domain.JobPosting;

class JobPostingMapper {

    private JobPostingMapper() {
    }

    static JobPostingInfo toInfo(JobPosting jobPosting, String authorName) {
        return new JobPostingInfo(
                jobPosting.getId(),
                jobPosting.getAuthorMemberId(),
                authorName,
                jobPosting.getTitle(),
                jobPosting.getCompanyName(),
                jobPosting.getCeoName(),
                jobPosting.getIndustry(),
                jobPosting.getAddress(),
                jobPosting.getContact(),
                jobPosting.getWebsiteUrl(),
                jobPosting.getCompanyDescription(),
                jobPosting.isBusinessRegistered(),
                jobPosting.isResumeRequired(),
                jobPosting.isCoverLetterRequired(),
                jobPosting.getRoles(),
                jobPosting.getGenres(),
                jobPosting.getWorkScope(),
                jobPosting.getDeadline(),
                jobPosting.getRecruitCount(),
                jobPosting.getHiringProcess(),
                jobPosting.getEducation(),
                jobPosting.getExperience(),
                jobPosting.getAge(),
                jobPosting.getGender(),
                jobPosting.getEmploymentType(),
                jobPosting.getWorkLocationType(),
                jobPosting.getWorkScheduleType(),
                jobPosting.getCoreTimeStart(),
                jobPosting.getCoreTimeEnd(),
                jobPosting.isHasOvertimePay(),
                jobPosting.isHasSocialInsurance(),
                jobPosting.isHasContract(),
                jobPosting.getPaymentType(),
                jobPosting.getPaymentUnit(),
                jobPosting.getMinAmount(),
                jobPosting.getMaxAmount(),
                jobPosting.isNegotiable(),
                jobPosting.getMgAmount(),
                jobPosting.getRsRatio(),
                jobPosting.isHasBuyout(),
                jobPosting.getBenefitDescription(),
                jobPosting.getBenefitKeywords(),
                jobPosting.getThumbnailImage(),
                jobPosting.getReferenceImages(),
                jobPosting.getBookmarkCount(),
                jobPosting.getViewCount(),
                jobPosting.getStatus(),
                jobPosting.getDeletedAt(),
                jobPosting.getCreatedAt(),
                jobPosting.getUpdatedAt()
        );
    }

    static CommunityJobPostingCardInfo toCardInfo(JobPosting jobPosting, String authorName) {
        return new CommunityJobPostingCardInfo(
                jobPosting.getId(),
                jobPosting.getThumbnailImage(),
                jobPosting.getTitle(),
                jobPosting.getCompanyName(),
                authorName,
                jobPosting.getDeadline(),
                jobPosting.getStatus() == JobPostingStatus.CLOSED
        );
    }
}
