package com.atcrew.member.internal.application;

import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import com.atcrew.member.internal.persistence.Member;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;

    MemberServiceImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional
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
    @Transactional
    public void updateProfile(Long memberId, String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus, ExperienceLevel experienceLevel) {
        findMemberById(memberId).updateProfile(name, profileImage, creatorRole, employmentStatus, experienceLevel);
    }

    @Override
    @Transactional
    public void updateDetails(Long memberId, String birthDate, String school, String location,
                              String contactEmail, String socialMediaLink, String twitter,
                              String desiredField, String creativeTools, String career,
                              List<String> keywords) {
        findMemberById(memberId).updateDetails(birthDate, school, location, contactEmail, socialMediaLink,
                twitter, desiredField, creativeTools, career, keywords);
    }

    @Override
    @Transactional
    public void deactivate(Long memberId) {
        findMemberById(memberId).deactivate();
    }

    @Override
    public MemberInfo findById(Long memberId) {
        return toInfo(findMemberById(memberId));
    }

    private Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, String.valueOf(memberId)));
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
                member.getExperienceLevel(),
                member.isActive(),
                member.getDeletedAt()
        );
    }
}
