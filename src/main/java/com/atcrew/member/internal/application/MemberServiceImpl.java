package com.atcrew.member.internal.application;

import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
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
    public void updateProfile(String memberId, String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus,
                              int totalSlotCount, int availableSlotCount,
                              List<TeamExperience> teamExperiences) {
        Member member = findMemberById(memberId);
        member.updateProfile(name, profileImage, creatorRole, employmentStatus,
                totalSlotCount, availableSlotCount, teamExperiences);
        memberRepository.save(member);
    }

    @Override
    public void updateDetails(String memberId, String location, String contactEmail, String socialMediaLink,
                              String twitter, String creativeTools, List<String> keywords) {
        Member member = findMemberById(memberId);
        member.updateDetails(location, contactEmail, socialMediaLink, twitter, creativeTools, keywords);
        memberRepository.save(member);
    }

    @Override
    public CareerEntryInfo addCareer(String memberId, String workTitle, String episodeCount,
                                     String startDate, String endDate, boolean ongoing, String description) {
        Member member = findMemberById(memberId);
        CareerEntryInfo entry = member.addCareer(workTitle, episodeCount, startDate, endDate, ongoing, description);
        memberRepository.save(member);
        return entry;
    }

    @Override
    public void updateCareer(String memberId, String careerId, String workTitle, String episodeCount,
                             String startDate, String endDate, boolean ongoing, String description) {
        Member member = findMemberById(memberId);
        member.updateCareer(careerId, workTitle, episodeCount, startDate, endDate, ongoing, description);
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
                member.getProfileImage(),
                member.getCreatorRole(),
                member.getEmploymentStatus(),
                member.getTotalSlotCount(),
                member.getAvailableSlotCount(),
                member.getTeamExperiences(),
                member.getContactEmail(),
                member.getSocialMediaLink(),
                member.getTwitter(),
                member.getCreativeTools(),
                member.getCareers(),
                member.getKeywords(),
                member.getExperienceLevel(),
                member.getBirthDate(),
                member.getSchool(),
                member.getLocation(),
                member.getDesiredField(),
                member.isActive(),
                member.getDeletedAt(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

}
