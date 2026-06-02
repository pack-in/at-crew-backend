package com.atcrew.member;

import com.atcrew.member.internal.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        if (memberRepository.existsByLoginEmail(loginEmail)) {
            throw new IllegalStateException("이미 가입된 이메일입니다: " + loginEmail);
        }
        if (memberRepository.existsByHandle(handle)) {
            throw new IllegalStateException("이미 사용 중인 핸들입니다: " + handle);
        }
        return memberRepository.save(Member.register(loginEmail, handle, name, creatorRole));
    }

    public Member findByHandle(String handle) {
        return memberRepository.findByHandle(handle)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 핸들입니다: " + handle));
    }

    public Member findByLoginEmail(String loginEmail) {
        return memberRepository.findByLoginEmail(loginEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 회원입니다: " + loginEmail));
    }

    @Transactional
    public void updateProfile(Long memberId, String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus, ExperienceLevel experienceLevel) {
        Member member = findById(memberId);
        member.updateProfile(name, profileImage, creatorRole, employmentStatus, experienceLevel);
    }

    @Transactional
    public void updateDetails(Long memberId, String birthDate, String school, String location,
                              String contactEmail, String socialMediaLink, String twitter,
                              String desiredField, String creativeTools, String career,
                              List<String> keywords) {
        Member member = findById(memberId);
        member.updateDetails(birthDate, school, location, contactEmail, socialMediaLink,
                twitter, desiredField, creativeTools, career, keywords);
    }

    @Transactional
    public void deactivate(Long memberId) {
        Member member = findById(memberId);
        member.deactivate();
    }

    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 회원입니다: " + memberId));
    }
}
