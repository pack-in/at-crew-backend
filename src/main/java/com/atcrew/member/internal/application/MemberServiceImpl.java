package com.atcrew.member.internal.application;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.PasswordVerification;
import com.atcrew.member.ProfileSort;
import com.atcrew.member.RegisterMemberCommand;
import com.atcrew.member.SearchProfilesCommand;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.domain.TermsAgreement;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberRepository;
import com.atcrew.common.logging.LogMask;
import com.atcrew.common.response.CursorPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberRepository memberRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    MemberServiceImpl(MemberRepository memberRepository, MongoTemplate mongoTemplate,
                      ApplicationEventPublisher eventPublisher, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        // 기동 시 랜덤 생성 — 코드 유출 시에도 더미 입력값 예측 불가
        this.dummyHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
    }

    @Override
    @Transactional
    public MemberInfo register(RegisterMemberCommand command) {
        if (memberRepository.existsByLoginEmailAndAuthProvider(command.loginEmail(), command.authProvider())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, command.loginEmail());
        }
        TermsAgreement terms = TermsAgreement.of(
                command.agreePrivacy(), command.agreeService(),
                command.agreeThirdParty(), command.agreeMarketing());
        for (int attempt = 0; attempt < 3; attempt++) {
            String handle = generateUniqueHandle(command.name());
            try {
                Member member = buildMember(command, handle, terms);
                return MemberMapper.toInfo(memberRepository.save(member));
            } catch (DuplicateKeyException e) {
                if (memberRepository.existsByLoginEmailAndAuthProvider(command.loginEmail(), command.authProvider())) {
                    throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, command.loginEmail());
                }
                log.debug("핸들 충돌 재시도: attempt={}", attempt + 1);
            }
        }
        throw new MemberException(MemberErrorCode.HANDLE_GENERATION_FAILED, command.name());
    }

    private Member buildMember(RegisterMemberCommand command, String handle, TermsAgreement terms) {
        if (command.authProvider() == AuthProvider.EMAIL) {
            String passwordHash = passwordEncoder.encode(command.rawPassword());
            return Member.registerWithEmail(command.loginEmail(), handle, command.name(), passwordHash, terms,
                    command.timezone());
        }
        return Member.registerWithGoogle(command.loginEmail(), handle, command.name(), terms, command.timezone());
    }

    @Override
    @Transactional
    public MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
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
    public PasswordVerification verifyPassword(String loginEmail, String rawPassword) {
        Optional<Member> found = memberRepository.findByLoginEmailAndAuthProvider(loginEmail, AuthProvider.EMAIL);
        if (found.isEmpty()) {
            // timing-safe: 회원 부재 경로도 BCrypt 연산 시간을 소비해 응답 시간 균일화
            passwordEncoder.matches(rawPassword, dummyHash);
            return PasswordVerification.mismatched();
        }
        Member member = found.get();
        if (!member.isActive()) {
            // defense-in-depth: 탈퇴 시 loginEmail=null로 클리어되어 이 조회에는 원래 걸리지 않는다.
            // 스키마 변경·마이그레이션 등 예외 상황 대비 유지.
            passwordEncoder.matches(rawPassword, dummyHash);
            return PasswordVerification.mismatched();
        }
        if (!member.hasPassword()) {
            return PasswordVerification.notSet();
        }
        return member.matchesPassword(rawPassword, passwordEncoder)
                ? PasswordVerification.matched(member.getId())
                : PasswordVerification.mismatched();
    }

    @Override
    @Transactional
    public void changePassword(String memberId, String rawNewPassword) {
        Member member = findMemberById(memberId);
        member.changePassword(passwordEncoder.encode(rawNewPassword));
        memberRepository.save(member);
    }

    private String generateUniqueHandle(String name) {
        String base = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (base.length() < 3) base = "user";
        if (base.length() > 12) base = base.substring(0, 12);
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
    public MemberInfo findByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider) {
        return MemberMapper.toInfo(memberRepository.findByLoginEmailAndAuthProvider(loginEmail, authProvider)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, loginEmail)));
    }

    @Override
    public MemberInfo findById(String memberId) {
        return MemberMapper.toInfo(findMemberById(memberId));
    }

    @Override
    public CursorPage<MemberProfileInfo> searchProfiles(SearchProfilesCommand command) {
        int limit = command.size() + 1;
        Criteria criteria = Criteria.where("active").is(true);
        if (command.employmentStatuses() != null && !command.employmentStatuses().isEmpty()) {
            criteria = criteria.and("employmentStatus").in(command.employmentStatuses().stream().map(Enum::name).toList());
        }
        if (command.activityField() != null) {
            criteria = criteria.and("activityFields").is(command.activityField().name());
        }
        ProfileSort sort = command.sort() != null ? command.sort() : ProfileSort.RECENTLY_UPDATED;
        Sort mongoSort;
        if (sort == ProfileSort.EXPERIENCE) {
            mongoSort = Sort.by(Sort.Direction.DESC, "experienceRank").and(Sort.by(Sort.Direction.DESC, "updatedAt"));
            if (command.cursor() != null) {
                ExperienceCursor c = parseExperienceCursor(command.cursor());
                criteria = criteria.orOperator(
                        Criteria.where("experienceRank").lt(c.rank()),
                        Criteria.where("experienceRank").is(c.rank()).and("updatedAt").lt(c.updatedAt()));
            }
        } else {
            mongoSort = Sort.by(Sort.Direction.DESC, "updatedAt");
            if (command.cursor() != null) {
                criteria = criteria.and("updatedAt").lt(parseCursor(command.cursor()));
            }
        }
        Query query = Query.query(criteria).with(mongoSort).limit(limit);
        List<Member> members = mongoTemplate.find(query, Member.class);
        return toProfilePage(members, command.size(), sort);
    }

    private CursorPage<MemberProfileInfo> toProfilePage(List<Member> members, int size, ProfileSort sort) {
        if (members.isEmpty()) return CursorPage.empty();
        boolean hasNext = members.size() > size;
        List<Member> page = hasNext ? members.subList(0, size) : members;
        List<MemberProfileInfo> items = page.stream().map(MemberMapper::toProfileInfo).toList();
        String nextCursor = null;
        if (hasNext) {
            Member last = page.get(page.size() - 1);
            nextCursor = sort == ProfileSort.EXPERIENCE
                    ? last.getExperienceRank() + "_" + last.getUpdatedAt().toEpochMilli()
                    : String.valueOf(last.getUpdatedAt().toEpochMilli());
        }
        return CursorPage.of(items, nextCursor);
    }

    private Instant parseCursor(String cursor) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(cursor));
        } catch (NumberFormatException e) {
            throw new MemberException(MemberErrorCode.INVALID_CURSOR);
        }
    }

    private record ExperienceCursor(int rank, Instant updatedAt) {
    }

    private ExperienceCursor parseExperienceCursor(String cursor) {
        try {
            String[] parts = cursor.split("_", 2);
            return new ExperienceCursor(Integer.parseInt(parts[0]), Instant.ofEpochMilli(Long.parseLong(parts[1])));
        } catch (RuntimeException e) {
            throw new MemberException(MemberErrorCode.INVALID_CURSOR);
        }
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
    public MemberInfo recordLogin(String memberId) {
        // N1: findAndModify로 find + update를 단일 MongoDB 쿼리로 처리 (auditing은 updatedAt 직접 설정)
        Query query = Query.query(Criteria.where("_id").is(memberId).and("active").is(true));
        Update update = new Update()
                .set("lastLoginAt", Instant.now())
                .set("updatedAt", Instant.now());
        FindAndModifyOptions opts = FindAndModifyOptions.options().returnNew(true);
        Member updated = mongoTemplate.findAndModify(query, update, opts, Member.class);
        if (updated == null) {
            memberRepository.findById(memberId)
                    .ifPresent(m -> { throw new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, memberId); });
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId);
        }
        return MemberMapper.toInfo(updated);
    }

    @Override
    @Transactional
    public void deactivate(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
        if (!member.isActive()) {
            return;
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
