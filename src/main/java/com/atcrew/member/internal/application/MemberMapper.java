package com.atcrew.member.internal.application;

import com.atcrew.member.MemberInfo;
import com.atcrew.member.internal.domain.Member;

class MemberMapper {

    private MemberMapper() {
    }

    static MemberInfo toInfo(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getHandle(),
                member.getLoginEmail(),
                member.getName(),
                member.getCreatorRole(),
                member.getEmploymentStatus(),
                member.getActivityFields(),
                member.getExperienceLevel(),
                member.getActiveRegions(),
                member.getTeamExperiences(),
                member.getTotalSlotCount(),
                member.getAvailableSlotCount(),
                member.getContact(),
                member.getSns(),
                member.getTools(),
                member.getCareers(),
                member.isActive(),
                member.getDeletedAt(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
