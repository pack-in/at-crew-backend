package com.atcrew.member.internal.application;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.RegisterMemberCommand;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.domain.TermsAgreement;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberRepository;
import com.atcrew.common.logging.LogMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    MemberServiceImpl(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public MemberInfo register(RegisterMemberCommand command) {
        if (memberRepository.existsByLoginEmail(command.loginEmail())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, command.loginEmail());
        }
        TermsAgreement terms = TermsAgreement.of(command.agreePrivacy(), command.agreeService(), command.agreeMarketing());
        for (int attempt = 0; attempt < 3; attempt++) {
            String handle = generateUniqueHandle(command.name());
            try {
                Member member = Member.register(
                        command.loginEmail(), handle, command.name(),
                        command.authProvider(), command.accountType(),
                        command.companyName(), terms);
                return MemberMapper.toInfo(memberRepository.save(member));
            } catch (DuplicateKeyException e) {
                if (memberRepository.existsByLoginEmail(command.loginEmail())) {
                    throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, command.loginEmail());
                }
                log.debug("핸들 충돌 재시도: attempt={}", attempt + 1);
            }
        }
        throw new MemberException(MemberErrorCode.HANDLE_GENERATION_FAILED, command.name());
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
        try {
            return MemberMapper.toInfo(memberRepository.save(Member.register(loginEmail, handle, name, creatorRole)));
        } catch (DuplicateKeyException e) {
            throw new MemberException(MemberErrorCode.DUPLICATE_MEMBER_INFO);
        }
    }

    @Override
    public boolean existsByLoginEmail(String loginEmail) {
        return memberRepository.existsByLoginEmail(loginEmail);
    }

    @Override
    public boolean isDeactivatedEmail(String loginEmail) {
        return memberRepository.existsByDeletedLoginEmail(loginEmail);
    }

    private String generateUniqueHandle(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.length() < 3) base = "user";
        if (base.length() > 12) base = base.substring(0, 12);
        // UUID 8자리 suffix — 충돌 가능성이 사실상 0에 수렴하므로 단순 생성 후 반환
        return base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Override
    public MemberProfileInfo findProfileByHandle(String handle) {
        return MemberMapper.toProfileInfo(memberRepository.findByHandle(handle)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, handle)));
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
    @Transactional
    public void updateName(String memberId, String name) {
        Member member = findMemberById(memberId);
        member.updateName(name);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void updateInfo(String memberId, UpdateInfoCommand command) {
        Member member = findMemberById(memberId);
        member.updateInfo(command);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public CareerEntryInfo addCareer(String memberId, AddCareerCommand command) {
        Member member = findMemberById(memberId);
        CareerEntryInfo entry = member.addCareer(command.workTitle(), command.role(),
                command.startDate(), command.endDate(), command.ongoing(), command.description());
        memberRepository.save(member);
        return entry;
    }

    @Override
    @Transactional
    public void deleteCareer(String memberId, String careerId) {
        Member member = findMemberById(memberId);
        member.deleteCareer(careerId);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public MemberInfo recordLogin(String memberId) {
        Member member = findMemberById(memberId);
        member.recordLogin();
        return MemberMapper.toInfo(memberRepository.save(member));
    }

    @Override
    @Transactional
    public void deactivate(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
        if (!member.isActive()) {
            return; // 이미 탈퇴 — 멱등 처리
        }
        member.deactivate();
        memberRepository.save(member);
        log.info("회원 탈퇴 처리: memberId={} email={}", memberId, LogMask.email(member.getDeletedLoginEmail()));
        eventPublisher.publishEvent(new MemberDeactivatedEvent(memberId));
    }

    private Member findMemberById(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
        if (!member.isActive()) {
            throw new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, memberId);
        }
        return member;
    }

}
