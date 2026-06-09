package com.atcrew.member.internal.application;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.TeamExperience;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import com.atcrew.member.internal.persistence.Member;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;

    MemberServiceImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        if (memberRepository.existsByLoginEmail(loginEmail)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, loginEmail);
        }
        if (memberRepository.existsByHandle(handle)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }
        return toInfo(memberRepository.save(Member.register(loginEmail, handle, name, creatorRole)));
    }

    @Override
    public MemberInfo findByHandle(String handle) {
        return toInfo(memberRepository.findByHandle(handle)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, handle)));
    }

    @Override
    public MemberInfo findByLoginEmail(String loginEmail) {
        return toInfo(memberRepository.findByLoginEmail(loginEmail)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, loginEmail)));
    }

    @Override
    public MemberInfo findById(String memberId) {
        return toInfo(findMemberById(memberId));
    }

    @Override
    public void updateProfile(String memberId, String name, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus,
                              List<ActivityField> activityFields,
                              ExperienceLevel experienceLevel,
                              List<ActiveRegion> activeRegions,
                              int totalSlotCount, int availableSlotCount,
                              List<TeamExperience> teamExperiences) {
        Member member = findMemberById(memberId);
        member.updateProfile(name, creatorRole, employmentStatus, activityFields,
                experienceLevel, activeRegions, totalSlotCount, availableSlotCount, teamExperiences);
        memberRepository.save(member);
    }

    @Override
    public void updateDetails(String memberId, String contact, String sns, String tools) {
        Member member = findMemberById(memberId);
        member.updateDetails(contact, sns, tools);
        memberRepository.save(member);
    }

    @Override
    public CareerEntryInfo addCareer(String memberId, String workTitle, String role,
                                     String startDate, String endDate, boolean ongoing, String description) {
        Member member = findMemberById(memberId);
        CareerEntryInfo entry = member.addCareer(workTitle, role, startDate, endDate, ongoing, description);
        memberRepository.save(member);
        return entry;
    }

    @Override
    public void updateCareer(String memberId, String careerId, String workTitle, String role,
                             String startDate, String endDate, boolean ongoing, String description) {
        Member member = findMemberById(memberId);
        member.updateCareer(careerId, workTitle, role, startDate, endDate, ongoing, description);
        memberRepository.save(member);
    }

    @Override
    public void deleteCareer(String memberId, String careerId) {
        Member member = findMemberById(memberId);
        member.deleteCareer(careerId);
        memberRepository.save(member);
    }

    @Override
    public void deactivate(String memberId) {
        Member member = findMemberById(memberId);
        member.deactivate();
        memberRepository.save(member);
    }

    private Member findMemberById(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
    }

    private MemberInfo toInfo(Member member) {
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
