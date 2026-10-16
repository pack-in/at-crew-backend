package com.atcrew.member.internal.application;

import com.atcrew.member.AccountDeletionFailedEvent;
import com.atcrew.member.AccountInfo;
import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.ArtistProfileViewedEvent;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.HandleAvailabilityInfo;
import com.atcrew.member.HandleChangeInfo;
import com.atcrew.member.Language;
import com.atcrew.member.MemberDeactivatedEvent;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberProfileInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.PasswordVerification;
import com.atcrew.member.ProfileSectionSavedEvent;
import com.atcrew.member.ProfileSort;
import com.atcrew.member.RegisterMemberCommand;
import com.atcrew.member.ResolvedHandle;
import com.atcrew.member.SearchProfilesCommand;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.domain.HandleValidator;
import com.atcrew.member.internal.domain.Member;
import com.atcrew.member.internal.domain.MemberHandle;
import com.atcrew.member.internal.domain.TermsAgreement;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import com.atcrew.member.internal.persistence.MemberHandleRepository;
import com.atcrew.member.internal.persistence.MemberRepository;
import com.atcrew.common.exception.DomainException;
import com.atcrew.common.logging.LogMask;
import com.atcrew.common.persistence.DuplicateKeyViolations;
import com.atcrew.common.response.OffsetPage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int HANDLE_GENERATION_ATTEMPTS = 3;

    private final MemberRepository memberRepository;
    private final MemberHandleRepository memberHandleRepository;
    private final MemberHandleChanger handleChanger;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    MemberServiceImpl(MemberRepository memberRepository, MemberHandleRepository memberHandleRepository,
                      MemberHandleChanger handleChanger, ApplicationEventPublisher eventPublisher,
                      PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.memberHandleRepository = memberHandleRepository;
        this.handleChanger = handleChanger;
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
        // 전환기: null뿐 아니라 빈 문자열·공백뿐인 값도 "입력 안 함"으로 보고 생성한다 — FE가 빈 입력란을 ""로 보낼 수 있다.
        boolean generated = command.handle() == null || command.handle().isBlank();
        String handle;
        if (generated) {
            handle = generateAvailableHandle(command.name());
        } else {
            handle = HandleValidator.validate(command.handle());
            if (memberHandleRepository.existsById(handle)) {
                throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
            }
        }
        Member member = buildMember(command, handle, terms);
        try {
            // 회원과 원장을 같은 트랜잭션에서 넣는다. saveAndFlush로 즉시 flush — unique 제약 위반이 이 try 블록
            // 안에서 동기적으로 터지게 한다. save()만 쓰면 Hibernate가 INSERT를 커밋 시점까지 지연시킨다.
            Member saved = memberRepository.saveAndFlush(member);
            memberHandleRepository.saveAndFlush(new MemberHandle(handle, saved.getId()));
            return MemberMapper.toInfo(saved);
        } catch (DataIntegrityViolationException e) {
            if (!DuplicateKeyViolations.isDuplicateKey(e)) {
                throw e;
            }
            // 사전 검사를 통과한 뒤의 동시 가입 경쟁. 같은 트랜잭션은 이미 롤백 전용이라 재시도할 수 없다.
            if (memberRepository.existsByLoginEmailAndAuthProvider(command.loginEmail(), command.authProvider())) {
                throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, command.loginEmail());
            }
            if (generated) {
                throw new MemberException(MemberErrorCode.HANDLE_GENERATION_FAILED, "handle=" + handle);
            }
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }
    }

    // TODO(PA-14): FE가 가입 화면에서 handle을 필수로 보내면 자동 생성 경로 전체(이 메서드·HANDLE_GENERATION_FAILED,
    //  register의 null·공백 판정)를 제거하고 빈 값은 HandleValidator의 HANDLE_REQUIRED로 거부한다 — FE 배포 전
    //  전환기 동안만 쓴다(member-handle-design §4.1).
    /**
     * 전환기 자동 생성 — {이름부 ≤8자}_{숫자 5자리}. 이름에서 영문·숫자만 남기고 4자 미만이면 user를 쓴다
     * (V52 일괄 변경과 같은 형식). 원장에 이미 있으면 최대 3회 다시 뽑는다. 원장 PK 위반 후에는 같은
     * 트랜잭션에서 재시도할 수 없으므로 충돌은 insert 전에 원장 조회로 거른다.
     */
    private String generateAvailableHandle(String name) {
        String base = name.replaceAll("[^A-Za-z0-9]", "");
        if (base.length() > 8) base = base.substring(0, 8);
        if (base.length() < HandleValidator.MIN_LENGTH) base = "user";
        for (int attempt = 0; attempt < HANDLE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = base + "_" + String.format(Locale.ROOT, "%05d", SECURE_RANDOM.nextInt(100_000));
            if (!memberHandleRepository.existsById(candidate)) {
                return candidate;
            }
            log.debug("자동 생성 handle 충돌 재추첨: attempt={}", attempt + 1);
        }
        throw new MemberException(MemberErrorCode.HANDLE_GENERATION_FAILED, "base=" + base);
    }

    private Member buildMember(RegisterMemberCommand command, String handle, TermsAgreement terms) {
        if (command.authProvider() == AuthProvider.EMAIL) {
            String passwordHash = passwordEncoder.encode(command.rawPassword());
            return Member.registerWithEmail(command.loginEmail(), handle, command.name(), passwordHash, terms,
                    command.timezone(), command.countryCode(), command.primaryLanguage());
        }
        return Member.registerWithGoogle(command.loginEmail(), handle, command.name(), terms,
                command.timezone(), command.countryCode(), command.primaryLanguage());
    }

    @Override
    @Transactional
    public MemberInfo register(String loginEmail, String handle, String name) {
        // 형식 검증은 하지 않는다 — 개발·테스트 전용 경로라 호출부(DevMemberController)가 HandleValidator로 검증한다.
        // 탈퇴 회원이 쓰던 handle도 원장에 남아 있어 재할당되지 않는다.
        if (memberHandleRepository.existsById(handle)) {
            throw new MemberException(MemberErrorCode.DUPLICATE_HANDLE, handle);
        }
        try {
            // saveAndFlush 이유는 위 register(RegisterMemberCommand) 주석 참고.
            Member saved = memberRepository.saveAndFlush(Member.register(loginEmail, handle, name));
            memberHandleRepository.saveAndFlush(new MemberHandle(handle, saved.getId()));
            return MemberMapper.toInfo(saved);
        } catch (DataIntegrityViolationException e) {
            if (!DuplicateKeyViolations.isDuplicateKey(e)) {
                throw e;
            }
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
        ResolvedHandle resolved = resolveHandle(handle);
        Member member = findMemberById(resolved.memberId());
        // 옛 handle로 들어온 요청(moved)은 FE가 현재 handle 주소로 리다이렉트해 다시 조회하므로, 여기서도 발행하면
        // 한 번 열람이 두 번 집계된다(member-handle-design §2.2).
        if (resolved.moved()) {
            return MemberMapper.toProfileInfo(member);
        }
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
    @Transactional(readOnly = true)
    public ResolvedHandle resolveHandle(String handle) {
        MemberHandle entry = memberHandleRepository.findById(handle)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, handle));
        Member owner = memberRepository.findById(entry.getMemberId())
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND,
                        "원장 소유 회원 없음: handle=" + handle));
        if (!owner.isActive()) {
            throw new MemberException(MemberErrorCode.MEMBER_WITHDRAWN, "handle=" + handle);
        }
        // 표시용 대소문자는 반드시 members.handle에서 가져온다 — 원장 PK는 대소문자 무관(_ci)이라 findById("ABC")가
        // 'abc' 행을 찾으면서 엔티티 id를 요청값으로 채울 수 있다. moved는 "요청값이 현재 handle과 같은 원장 행인가"이고,
        // 경로 변수는 ASCII로 제한되므로 원장 collation 비교와 equalsIgnoreCase가 같다.
        String current = owner.getHandle();
        return new ResolvedHandle(owner.getId(), current, !current.equalsIgnoreCase(handle));
    }

    /**
     * 트랜잭션 경계 밖 래퍼다 — 본체는 {@link MemberHandleChanger#change}가 자기 트랜잭션에서 수행한다. 같은 회원의
     * 동시 요청에서 진 쪽({@code HANDLE_CHANGE_CONFLICT})은 그 트랜잭션이 롤백된 뒤 새 읽기 트랜잭션으로 다시 보고,
     * 이긴 요청이 같은 값을 이미 반영했으면(더블클릭) 성공으로 응답한다. 다른 값끼리의 경쟁은 그대로 충돌이다.
     */
    @Override
    public HandleChangeInfo changeHandle(String memberId, String requested) {
        try {
            return handleChanger.change(memberId, requested);
        } catch (MemberException e) {
            if (!MemberErrorCode.HANDLE_CHANGE_CONFLICT.name().equals(e.getCode())) {
                throw e;
            }
            Optional<HandleChangeInfo> replay = handleChanger.findConcurrentReplay(memberId, requested);
            if (replay.isEmpty()) {
                throw e;
            }
            log.info("같은 값 동시 handle 변경을 멱등 성공으로 응답: memberId={}", memberId);
            return replay.get();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HandleAvailabilityInfo checkHandleAvailability(String handle, String viewerMemberId) {
        Optional<MemberErrorCode> invalid = HandleValidator.check(handle);
        if (invalid.isPresent()) {
            return new HandleAvailabilityInfo(false, invalid.get().name());
        }
        String value = handle.strip();
        // 로그인 본인의 현재 handle과 대소문자까지 같으면 변경 API와 같은 HANDLE_UNCHANGED다. 대소문자만 다르거나
        // 본인의 옛 handle이면 변경 가능하므로 사용 가능으로 둔다(§1.3 4단계와 같은 판정).
        if (viewerMemberId != null && memberRepository.findById(viewerMemberId)
                .map(viewer -> value.equals(viewer.getHandle()))
                .orElse(false)) {
            return new HandleAvailabilityInfo(false, MemberErrorCode.HANDLE_UNCHANGED.name());
        }
        // 다른 회원 소유면 활성·탈퇴를 구분하지 않고 DUPLICATE_HANDLE 하나로 답한다.
        boolean takenByOther = memberHandleRepository.findById(value)
                .map(owner -> !owner.getMemberId().equals(viewerMemberId))
                .orElse(false);
        return takenByOther
                ? new HandleAvailabilityInfo(false, MemberErrorCode.DUPLICATE_HANDLE.name())
                : new HandleAvailabilityInfo(true, null);
    }

    @Override
    public MemberInfo findByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider) {
        return MemberMapper.toInfo(memberRepository.findByLoginEmailAndAuthProvider(loginEmail, authProvider)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, loginEmail)));
    }

    @Override
    public Optional<MemberInfo> findActiveByLoginEmailAndProviderOrEmpty(String loginEmail, AuthProvider authProvider) {
        return memberRepository.findByLoginEmailAndAuthProvider(loginEmail, authProvider)
                .filter(Member::isActive)
                .map(MemberMapper::toInfo);
    }

    @Override
    public MemberInfo findById(String memberId) {
        return MemberMapper.toInfo(findMemberById(memberId));
    }

    @Override
    public Map<String, MemberInfo> findAllByIds(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        // findMemberById와 달리 없는 ID·탈퇴 회원에 예외를 던지지 않고 결과에서 뺀다.
        // 목록 조회에서 작가 한 명의 상태 때문에 페이지 전체가 실패하면 안 된다.
        return memberRepository.findAllById(memberIds).stream()
                .filter(Member::isActive)
                .collect(java.util.stream.Collectors.toMap(Member::getId, MemberMapper::toInfo));
    }

    @Override
    public OffsetPage<MemberProfileInfo> searchProfiles(SearchProfilesCommand command) {
        ProfileSort sort = command.sort() != null ? command.sort() : ProfileSort.RECENTLY_UPDATED;
        Specification<Member> spec = buildSearchSpecification(command);
        // 전체 개수는 이 조회가 이미 센 값을 쓴다 — 노출 조건 subquery가 7개라 COUNT를 두 번 돌리면 비싸다.
        Page<Member> found = memberRepository.findAll(spec,
                PageRequest.of(command.page() - 1, command.size(), sortOf(sort)));
        return new OffsetPage<>(found.getContent().stream().map(MemberMapper::toProfileInfo).toList(),
                found.getTotalElements());
    }

    /**
     * 정렬은 <b>(정렬 키, id)</b> 2단이다. 정렬 키 하나만으로는 값이 같은 행들의 순서를 DB가 보장하지 않아
     * 페이지 경계에서 회원이 빠지거나 중복된다(artwork 피드와 같은 규칙, 이슈 #78·#196).
     */
    private Sort sortOf(ProfileSort sort) {
        return switch (sort) {
            case EXPERIENCE -> Sort.by(Sort.Direction.DESC, "experienceRank", "updatedAt", "id");
            case VIEW_COUNT -> Sort.by(Sort.Direction.DESC, "profileViewCount", "updatedAt", "id");
            case RECENTLY_UPDATED -> Sort.by(Sort.Direction.DESC, "updatedAt", "id");
        };
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
    private Specification<Member> buildSearchSpecification(SearchProfilesCommand command) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (command.employmentStatuses() != null && !command.employmentStatuses().isEmpty()) {
                predicates.add(root.get("employmentStatus").in(command.employmentStatuses()));
            }
            predicates.addAll(buildExposurePredicates(root, cb));
            if (command.activityField() != null) {
                predicates.add(cb.isMember(command.activityField(), root.get("activityFields")));
            }
            // 언어 세그먼트(로그인-R16) — 작가는 작품과 달리 계정의 주 사용 언어로 판정한다.
            // 주 사용 언어가 없는 마이그레이션 이전 회원은 항상 노출한다(필터 도입 폴백).
            if (command.viewerLanguages() != null && !command.viewerLanguages().isEmpty()) {
                predicates.add(cb.or(
                        cb.isNull(root.get("primaryLanguage")),
                        root.get("primaryLanguage").in(command.viewerLanguages())));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 작가 찾기 노출 조건 (기획서 마이페이지_작가-R08).
     *
     * <p>구인 가능 상태여도 노출 대상 항목이 비어 있으면 목록에 나오지 않는다. 정본이 정한 7개 항목
     * 전체를 검사한다 — 사용자 이름·활동 분야·활동 경력·희망 담당 업무·희망 장르·희망 채용 형태·연락처.
     *
     * <p>복수 선택 항목은 null이 아니라 <b>빈 컬렉션</b>인지로 판정한다.
     */
    private List<Predicate> buildExposurePredicates(Root<Member> root, CriteriaBuilder cb) {
        return List.of(
                notBlank(cb, root.get("name")),
                cb.isNotEmpty(root.get("activityFields")),
                cb.isNotNull(root.get("experienceLevel")),
                cb.isNotEmpty(root.get("desiredRoles")),
                cb.isNotEmpty(root.get("desiredGenres")),
                cb.isNotEmpty(root.get("desiredEmploymentTypes")),
                notBlank(cb, root.get("contact"))
        );
    }

    // 연락처는 빈 문자열 전송으로 삭제할 수 있어(UpdateInfoRequest) null과 "" 둘 다 미입력으로 본다.
    private Predicate notBlank(CriteriaBuilder cb, Path<String> path) {
        return cb.and(cb.isNotNull(path), cb.notEqual(path, ""));
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
    public void updatePostLanguages(String memberId, Set<Language> languages) {
        Member member = findMemberById(memberId);
        member.updatePostLanguages(languages);
        memberRepository.save(member);
    }

    @Override
    public AccountInfo getAccount(String memberId) {
        Member member = findMemberById(memberId);
        return new AccountInfo(
                member.getLoginEmail(),
                member.getAuthProvider(),
                member.getPrimaryLanguage(),
                member.getPostLanguages(),
                member.isMarketingAgreed(),
                member.isAdultContentVisible());
    }

    @Override
    public List<Language> findPostLanguages(String memberId) {
        if (memberId == null) {
            return List.of();
        }
        return memberRepository.findById(memberId)
                .map(Member::getPostLanguages)
                .orElseGet(List::of);
    }

    @Override
    public LocalDate todayOf(String memberId) {
        return LocalDate.now(findMemberById(memberId).zoneId());
    }

    @Override
    public boolean isAdultContentVisible(String memberId) {
        if (memberId == null) {
            return true;
        }
        return memberRepository.findById(memberId)
                .map(Member::isAdultContentVisible)
                .orElse(true);
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
        List<Object> before = member.profileFieldSnapshot();
        member.updateInfo(command);
        memberRepository.save(member);
        List<Object> after = member.profileFieldSnapshot();
        Instant now = Instant.now();
        // 저장 API는 기본정보/구직정보를 한 번에 받지만, 실제로 바뀐 필드를 보고 섹션을 가려
        // 둘 다 바뀌면 이벤트를 2개로 나눠 낸다(FE 회신 PH-02, 2026-10-07).
        publishProfileSectionSavedIfChanged(memberId, "artist_info",
                ProfileCompletionCalculator.ARTIST_INFO_INDEXES, before, after, now);
        publishProfileSectionSavedIfChanged(memberId, "job_info",
                ProfileCompletionCalculator.JOB_INFO_INDEXES, before, after, now);
    }

    private void publishProfileSectionSavedIfChanged(String memberId, String sectionType,
            List<Integer> sectionIndexes, List<Object> before, List<Object> after, Instant occurredAt) {
        int changedFieldCount = ProfileCompletionCalculator.sectionChangedFieldCount(before, after, sectionIndexes);
        if (changedFieldCount == 0) {
            return;
        }
        double previousCompletionRate = ProfileCompletionCalculator.sectionCompletionRate(before, sectionIndexes);
        double completionRate = ProfileCompletionCalculator.sectionCompletionRate(after, sectionIndexes);
        boolean isComplete = completionRate == 1.0;
        boolean isFirstCompletion = isComplete && previousCompletionRate < 1.0;
        eventPublisher.publishEvent(new ProfileSectionSavedEvent(memberId, sectionType,
                completionRate, isComplete, changedFieldCount, isFirstCompletion, occurredAt));
    }

    @Override
    @Transactional
    public CareerEntryInfo addCareer(String memberId, AddCareerCommand command) {
        Member member = findMemberById(memberId);
        boolean hadCareerBefore = !member.getCareers().isEmpty();
        CareerEntryInfo entry = member.addCareer(command.workTitle(), command.role(),
                command.startDate(), command.endDate(), command.ongoing(), command.description(),
                LocalDate.now(member.zoneId()));
        memberRepository.save(member);
        publishCareerSectionSaved(member, !hadCareerBefore);
        return entry;
    }

    @Override
    @Transactional
    public void deleteCareer(String memberId, String careerId) {
        Member member = findMemberById(memberId);
        member.deleteCareer(careerId);
        memberRepository.save(member);
        publishCareerSectionSaved(member, false);
    }

    // 경력은 별도 API(POST/DELETE /members/me/careers)라 profile_section_saved를 career_info로
    // 따로 낸다(FE 회신 PH-02, 2026-10-07). completion_rate는 "경력 1건 이상 있음"을 1.0으로 보는
    // 이분법이다 — UpdateInfoCommand 23개 필드와 달리 경력은 개수 제한 없는 리스트라 비율 개념이 안 맞는다.
    private void publishCareerSectionSaved(Member member, boolean isFirstCompletion) {
        boolean hasAny = !member.getCareers().isEmpty();
        eventPublisher.publishEvent(new ProfileSectionSavedEvent(member.getId(), "career_info",
                hasAny ? 1.0 : 0.0, hasAny, 1, isFirstCompletion, Instant.now()));
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
        try {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND, memberId));
            if (!member.isActive()) {
                return;
            }
            member.deactivate();
            memberRepository.save(member);
            log.info("회원 탈퇴 처리: memberId={} email={}", memberId, LogMask.email(member.getDeletedLoginEmail()));
            eventPublisher.publishEvent(new MemberDeactivatedEvent(memberId));
        } catch (RuntimeException e) {
            eventPublisher.publishEvent(
                    new AccountDeletionFailedEvent(memberId, DomainException.codeOf(e), Instant.now()));
            throw e;
        }
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
