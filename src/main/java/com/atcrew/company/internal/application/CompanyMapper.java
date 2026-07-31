package com.atcrew.company.internal.application;

import com.atcrew.company.CompanyCareerInfo;
import com.atcrew.company.CompanyInfo;
import com.atcrew.company.internal.domain.Company;
import com.atcrew.company.internal.domain.CompanyCareer;

import java.util.List;

class CompanyMapper {

    private CompanyMapper() {
    }

    static CompanyInfo toInfo(Company company, boolean hasOpenJobPosting, boolean isOwner) {
        return new CompanyInfo(
                company.getId(),
                company.getMemberId(),
                company.getCompanyName(),
                company.getContact(),
                company.getSns(),
                company.getRecruitStatus(),
                company.getCompanyType(),
                company.hasBusinessRegistration(),
                // Set은 순서가 보장되지 않으므로 enum 선언 순으로 정렬해 응답 순서를 고정한다.
                company.getActivityFields().stream().sorted().toList(),
                company.getActiveRegions().stream().sorted().toList(),
                hasOpenJobPosting,
                isOwner,
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    static CompanyCareerInfo toCareerInfo(CompanyCareer career) {
        return new CompanyCareerInfo(
                career.getId(),
                career.getWorkTitle(),
                career.getStartDate(),
                career.getEndDate(),
                career.isOngoing(),
                career.getDescription()
        );
    }

    static List<CompanyCareerInfo> toCareerInfos(List<CompanyCareer> careers) {
        return careers.stream().map(CompanyMapper::toCareerInfo).toList();
    }
}
