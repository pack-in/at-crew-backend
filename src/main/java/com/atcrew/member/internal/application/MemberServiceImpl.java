package com.atcrew.member.internal.application;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.ArtistProfileViewedEvent;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    MemberServiceImpl(MemberRepository memberRepository,
                      ApplicationEventPublisher eventPublisher, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
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
                // saveAndFlush로 즉시 flush — handle unique 제약 위반이 이 try 블록 안에서 동기적으로 터지게 한다.
                // save()만 쓰면 Hibernate가 INSERT를 커밋 시점까지 지연시켜 재시도 로직이 무력화된다.
                return MemberMapper.toInfo(memberRepository.saveAndFlush(member));
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
                    command.timezone(), command.countryCode());
        }
        return Member.registerWithGoogle(command.loginEmail(), handle, command.name(), terms,
                command.timezone(), command.countryCode());
    }

    @Override
    @Transactional
    public MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        if (memberRepository.existsByHandle(handle)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }
        try {
            // saveAndFlush 이유는 위 register(RegisterMemberCommand) 주석 참고.
            return MemberMapper.toInfo(memberRepository.saveAndFlush(Member.register(loginEmail, handle, name, creatorRole)));
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
        // 주의(Spring 셀프 호출): 이 메서드를 거쳐 findProfileByHandle(handle, viewerMemberId)를 호출하면
        // 프록시를 우회해 그 메서드의 @Transactional이 적용되지 않는다 — 호출자가 이미 트랜잭션 안에 있지
        // 않다면 발행된 ArtistProfileViewedEvent가 버려질 수 있다. 뷰 이벤트 발행을 보장하려면
        // findProfileByHandle(handle, viewerMemberId)를 프록시(빈)를 통해 직접 호출해야 한다
        // (MemberController가 그렇게 호출한다). 현재 이 오버로드의 실제 호출자는 없다(하위 호환용).
        return findProfileByHandle(handle, null);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberProfileInfo findProfileByHandle(String handle, String viewerMemberId) {
        Member member = memberRepository.findByHandle(handle)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, handle));
        // 본인이 본인 프로필을 조회한 경우는 "작가를 조회했다"는 신호가 아니므로 이벤트를 발행하지 않는다.
        // 비로그인 조회(viewerMemberId == null)는 발행하되, 기록 대상에서 제외할지는 구독자가 판단한다
        // (ArtistProfileViewedEvent 참고). @Transactional readOnly로 감싸는 이유: 트랜잭션 커밋 후에만
        // 실행되는 @ApplicationModuleListener(AFTER_COMMIT)가 트랜잭션 없이 발행된 이벤트는 그냥 버리기 때문.
        if (!Objects.equals(viewerMemberId, member.getId())) {
            eventPublisher.publishEvent(new ArtistProfileViewedEvent(viewerMemberId, member.getId(), Instant.now()));
        }
        return MemberMapper.toProfileInfo(member);
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
        ProfileSort sort = command.sort() != null ? command.sort() : ProfileSort.RECENTLY_UPDATED;

        Specification<Member> spec = buildSearchSpecification(command, sort);
        Sort jpaSort = sort == ProfileSort.EXPERIENCE
                ? Sort.by(Sort.Direction.DESC, "experienceRank").and(Sort.by(Sort.Direction.DESC, "updatedAt"))
                : Sort.by(Sort.Direction.DESC, "updatedAt");

        List<Member> members = memberRepository.findAll(spec, PageRequest.of(0, limit, jpaSort)).getContent();
        return toProfilePage(members, command.size(), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findIdsByKeyword(List<String> memberIds, String keyword) {
        if (memberIds == null || memberIds.isEmpty() || keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return memberRepository.findIdsByKeyword(memberIds, keyword);
    }

    // Mongo Criteria 동적 쿼리 → JPA Specification (docs/design/mariadb-migration-design.md §3.6)
    private Specification<Member> buildSearchSpecification(SearchProfilesCommand command, ProfileSort sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (command.employmentStatuses() != null && !command.employmentStatuses().isEmpty()) {
                predicates.add(root.get("employmentStatus").in(command.employmentStatuses()));
            }
            if (command.activityField() != null) {
                predicates.add(cb.isMember(command.activityField(), root.get("activityFields")));
            }
            if (command.cursor() != null) {
                predicates.add(buildCursorPredicate(root, cb, sort, command.cursor()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 기존 복합 커서(keyset) 비교 로직을 SQL 표준 형태로 그대로 이식 — 정렬 기준별 분기 무변경 (§3.6)
    private Predicate buildCursorPredicate(Root<Member> root, CriteriaBuilder cb, ProfileSort sort, String cursor) {
        if (sort == ProfileSort.EXPERIENCE) {
            ExperienceCursor c = parseExperienceCursor(cursor);
            return cb.or(
                    cb.lessThan(root.get("experienceRank"), c.rank()),
                    cb.and(cb.equal(root.get("experienceRank"), c.rank()),
                           cb.lessThan(root.get("updatedAt"), c.updatedAt())));
        }
        return cb.lessThan(root.get("updatedAt"), parseCursor(cursor));
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
    public void updateMarketingAgreement(String memberId, boolean agreed) {
        Member member = findMemberById(memberId);
        member.updateMarketingAgreement(agreed);
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void updateAdultContentVisible(String memberId, boolean visible) {
        Member member = findMemberById(memberId);
        member.updateAdultContentVisible(visible);
        memberRepository.save(member);
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
        // Mongo findAndModify → 조건부 UPDATE + 영향 행 수 판별 (§3.3.1)
        int updated = memberRepository.recordLogin(memberId, Instant.now());
        if (updated == 0) {
            memberRepository.findById(memberId)
                    .ifPresent(m -> { throw new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, memberId); });
            throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId);
        }
        return MemberMapper.toInfo(memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId)));
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
