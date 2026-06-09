package com.atcrew.member.internal.application;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateCareerCommand;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.persistence.MemberRepository;
import org.springframework.stereotype.Service;

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
        return MemberMapper.toInfo(memberRepository.save(Member.register(loginEmail, handle, name, creatorRole)));
    }

    @Override
    public MemberInfo findByHandle(String handle) {
        return MemberMapper.toInfo(memberRepository.findByHandle(handle)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, handle)));
    }

    @Override
    public MemberInfo findByLoginEmail(String loginEmail) {
        return MemberMapper.toInfo(memberRepository.findByLoginEmail(loginEmail)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, loginEmail)));
    }

    @Override
    public MemberInfo findById(String memberId) {
        return MemberMapper.toInfo(findMemberById(memberId));
    }

    @Override
    public void updateName(String memberId, String name) {
        Member member = findMemberById(memberId);
        member.updateName(name);
        memberRepository.save(member);
    }

    @Override
    public void updateInfo(String memberId, UpdateInfoCommand command) {
        Member member = findMemberById(memberId);
        member.updateInfo(command);
        memberRepository.save(member);
    }

    @Override
    public CareerEntryInfo addCareer(String memberId, AddCareerCommand command) {
        Member member = findMemberById(memberId);
        CareerEntryInfo entry = member.addCareer(command.workTitle(), command.role(),
                command.startDate(), command.endDate(), command.ongoing(), command.description());
        memberRepository.save(member);
        return entry;
    }

    @Override
    public void updateCareer(String memberId, String careerId, UpdateCareerCommand command) {
        Member member = findMemberById(memberId);
        member.updateCareer(careerId, command.workTitle(), command.role(),
                command.startDate(), command.endDate(), command.ongoing(), command.description());
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

}
