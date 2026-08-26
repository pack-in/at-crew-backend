package com.atcrew.member.internal.application;

import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.internal.domain.Member;

class MemberMapper {

    private MemberMapper() {
    }

    static MemberProfileInfo toProfileInfo(Member member) {
        return new MemberProfileInfo(
                member.getId(),
                member.getHandle(),
                member.getName(),
                member.getEmploymentStatus(),
                member.getActivityFields(),
                member.getExperienceLevel(),
                member.getActiveRegion(),
                member.getTeamExperiences(),
                member.getProfileViewCount(),
                member.getTotalSlotCount(),
                member.getAvailableSlotCount(),
                member.getContact(),
                member.getSns(),
                member.getTools(),
                member.getCareers(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    static MemberInfo toInfo(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getHandle(),
                member.getLoginEmail(),
                member.getAuthProvider(),
                member.getName(),
                member.getEmploymentStatus(),
                member.getActivityFields(),
                member.getExperienceLevel(),
                member.getActiveRegion(),
                member.getTeamExperiences(),
                member.getTotalSlotCount(),
                member.getAvailableSlotCount(),
                member.getContact(),
                member.getSns(),
                member.getTools(),
                member.getCareers(),
                member.isActive(),
                member.getDeletedAt(),
                member.getLastLoginAt(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                member.getTimezone(),
                member.getCountryCode(),
                member.isMarketingAgreed(),
                member.isAdultContentVisible()
        );
    }
}
