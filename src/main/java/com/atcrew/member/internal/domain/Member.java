package com.atcrew.member.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "members")
@EntityListeners(AuditingEntityListener.class)
public class Member implements Persistable<String> {

    @Id
    private String id;

    // 탈퇴 시 null로 클리어 — 복합 unique 인덱스(loginEmail, authProvider)로 재가입 충돌 방지
    private String loginEmail;

    private String handle;

    private String name;

    @Enumerated(EnumType.STRING)
    private CreatorRole creatorRole;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Embedded
    @AttributeOverride(name = "privacyPolicy", column = @Column(name = "terms_privacy_policy"))
    @AttributeOverride(name = "serviceTerms", column = @Column(name = "terms_service_terms"))
    @AttributeOverride(name = "thirdPartyProvision", column = @Column(name = "terms_third_party"))
    @AttributeOverride(name = "marketingNotification", column = @Column(name = "terms_marketing"))
    @AttributeOverride(name = "agreedAt", column = @Column(name = "terms_agreed_at"))
    private TermsAgreement termsAgreement;

    // EMAIL provider 전용. GOOGLE 회원·마이그레이션 미전환 회원은 null.
    // 절대 MemberInfo 등 공개 레코드로 노출하지 않는다.
    private String passwordHash;

    // 기본 false. Google 가입 시 true로 초기화. 이메일 인증 캠페인 대비 선제 추가.
    private boolean emailVerified = false;

    // IANA tz ID(예: "Asia/Tokyo", "America/New_York"). 가입 시 클라이언트 자동감지값을 저장,
    // 설정에서 변경 가능. UTC 오프셋이 아닌 ID로 저장해야 DST가 자동 반영된다.
    private String timezone;

    // ISO 3166-1 alpha-2 국가 코드(예: "KR", "JP"). 거주 국가 — 가입 시 필수 수집, 설정에서 변경 가능.
    private String countryCode;

    @Enumerated(EnumType.STRING)
    private EmploymentStatus employmentStatus = EmploymentStatus.PREPARING;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_activity_fields", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "activity_field")
    @Enumerated(EnumType.STRING)
    private Set<ActivityField> activityFields = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    // experienceLevel은 @Enumerated(STRING) 컬럼이라 그대로는 경력순 정렬(사전순≠경력순)이 안 되므로
    // ordinal을 별도 필드로 캐시해 DB 레벨 정렬에 사용한다 (커뮤니티 "작가 찾아보기" 경력순 정렬).
    private int experienceRank = -1;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_active_regions", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<ActiveRegion> activeRegions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_team_experiences", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<TeamExperience> teamExperiences = new HashSet<>();

    private int totalSlotCount = 5;
    private int availableSlotCount = 5;

    private String contact;
    private String sns;
    private String tools;

    // 양방향 매핑(mappedBy) — 단방향 @JoinColumn은 Hibernate가 INSERT 시 FK 없이 먼저 쓰고
    // 뒤이어 UPDATE로 채우는 2단계 패턴이라 member_id NOT NULL 제약과 충돌한다.
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC") // id가 UUIDv7(시간순)라 삽입 순서와 근사 일치
    private List<CareerEntry> careers = new ArrayList<>();

    @Column(name = "is_active")
    private boolean active = true;

    private Instant deletedAt;
    private Instant lastLoginAt;

    // 탈퇴 시 loginEmail 백업 (감사 추적용)
    private String deletedLoginEmail;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // MariaDB 전환(docs/design/mariadb-migration-design.md §3.1) — 애플리케이션이 ID를 직접 할당하므로
    // Persistable로 신규 여부를 명시해 신규 엔티티는 매번 merge()(선행 SELECT) 대신 persist()로 처리되게 한다.
    @Transient
    private boolean isNew = false;

    protected Member() {
    }

    private Member(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        this.id = UuidV7Generator.generate();
        this.loginEmail = loginEmail;
        this.handle = handle;
        this.name = name;
        this.creatorRole = creatorRole;
        this.isNew = true;
    }

    // 개발·테스트 전용 (authProvider 없이 직접 가입)
    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public static Member registerWithEmail(String loginEmail, String handle, String name,
                                           String passwordHash, TermsAgreement termsAgreement,
                                           String timezone, String countryCode) {
        validateTerms(termsAgreement);
        validateTimezone(timezone);
        validateCountryCode(countryCode);
        Member m = new Member(loginEmail, handle, name, null);
        m.authProvider = AuthProvider.EMAIL;
        m.passwordHash = passwordHash;
        m.emailVerified = false;
        m.termsAgreement = termsAgreement;
        m.timezone = timezone;
        m.countryCode = countryCode;
        return m;
    }

    public static Member registerWithGoogle(String loginEmail, String handle, String name,
                                            TermsAgreement termsAgreement, String timezone, String countryCode) {
        validateTerms(termsAgreement);
        validateTimezone(timezone);
        validateCountryCode(countryCode);
        Member m = new Member(loginEmail, handle, name, null);
        m.authProvider = AuthProvider.GOOGLE;
        m.emailVerified = true;
        m.termsAgreement = termsAgreement;
        m.timezone = timezone;
        m.countryCode = countryCode;
        return m;
    }

    private static void validateTerms(TermsAgreement terms) {
        if (terms == null || !terms.privacyPolicy() || !terms.serviceTerms() || !terms.thirdPartyProvision()) {
            throw new MemberException(MemberErrorCode.TERMS_NOT_AGREED);
        }
    }

    // IANA tz ID만 허용 — ZoneId.of()는 "+09:00"·"GMT+9" 같은 고정 오프셋도 통과시키므로
    // 오프셋 저장을 원천 차단하려면 실제 IANA tzdb 카탈로그(getAvailableZoneIds)에 있는지 확인해야 한다.
    // DST 지역(미국·유럽)에서 오프셋 저장은 연 2회 어긋난다.
    private static void validateTimezone(String timezone) {
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new MemberException(MemberErrorCode.INVALID_TIMEZONE, timezone);
        }
    }

    // ISO 3166-1 alpha-2만 허용 — JDK 내장 카탈로그(Locale.getISOCountries())로 임의 문자열을 원천 차단한다.
    private static void validateCountryCode(String countryCode) {
        if (countryCode == null || !Set.of(Locale.getISOCountries()).contains(countryCode)) {
            throw new MemberException(MemberErrorCode.INVALID_COUNTRY, countryCode);
        }
    }

    public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
        return passwordHash != null && encoder.matches(rawPassword, passwordHash);
    }

    public void changePassword(String newPasswordHash) {
        assertActive();
        this.passwordHash = newPasswordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void updateName(String name) {
        assertActive();
        this.name = name;
    }

    public void updateInfo(com.atcrew.member.UpdateInfoCommand command) {
        assertActive();
        Integer totalSlotCount = command.totalSlotCount();
        Integer availableSlotCount = command.availableSlotCount();
        int effectiveTotal = totalSlotCount != null ? totalSlotCount : this.totalSlotCount;
        int effectiveAvailable = availableSlotCount != null ? availableSlotCount : this.availableSlotCount;
        if (effectiveAvailable > effectiveTotal) {
            if (availableSlotCount != null) {
                throw new MemberException(MemberErrorCode.INVALID_SLOT_COUNT,
                        "available=" + effectiveAvailable + " total=" + effectiveTotal);
            }
            effectiveAvailable = effectiveTotal;
        }
        if (command.creatorRole() != null) this.creatorRole = command.creatorRole();
        if (command.employmentStatus() != null) this.employmentStatus = command.employmentStatus();
        if (command.activityFields() != null) this.activityFields = new HashSet<>(command.activityFields());
        if (command.experienceLevel() != null) {
            this.experienceLevel = command.experienceLevel();
            this.experienceRank = command.experienceLevel().ordinal();
        }
        if (command.activeRegions() != null) this.activeRegions = new HashSet<>(command.activeRegions());
        if (totalSlotCount != null) this.totalSlotCount = effectiveTotal;
        if (totalSlotCount != null || availableSlotCount != null) this.availableSlotCount = effectiveAvailable;
        if (command.teamExperiences() != null) this.teamExperiences = new HashSet<>(command.teamExperiences());
        if (command.contact() != null) this.contact = command.contact();
        if (command.sns() != null) this.sns = command.sns();
        if (command.tools() != null) this.tools = command.tools();
        if (command.timezone() != null) {
            validateTimezone(command.timezone());
            this.timezone = command.timezone();
        }
        if (command.countryCode() != null) {
            validateCountryCode(command.countryCode());
            this.countryCode = command.countryCode();
        }
    }

    private static final int MAX_CAREER_COUNT = 50;

    public CareerEntryInfo addCareer(String workTitle, String role, LocalDate startDate,
                                     LocalDate endDate, boolean ongoing, String description) {
        assertActive();
        if (careers.size() >= MAX_CAREER_COUNT) {
            throw new MemberException(MemberErrorCode.CAREER_LIMIT_EXCEEDED);
        }
        validateCareerPeriod(startDate, endDate, ongoing);
        CareerEntry entry = new CareerEntry(UuidV7Generator.generate(), this, workTitle, role,
                startDate, endDate, ongoing, description);
        this.careers.add(entry);
        return toCareerInfo(entry);
    }

    public void deleteCareer(String careerId) {
        assertActive();
        boolean removed = careers.removeIf(c -> c.getId().equals(careerId));
        if (!removed) {
            throw new MemberException(MemberErrorCode.CAREER_NOT_FOUND, careerId);
        }
    }

    public void recordLogin() {
        assertActive();
        this.lastLoginAt = Instant.now();
    }

    public void deactivate() {
        assertActive();
        this.active = false;
        this.deletedAt = Instant.now();
        this.deletedLoginEmail = this.loginEmail;
        this.loginEmail = null;
        this.handle = null;
        this.passwordHash = null;
    }

    @Override
    public String getId() { return id; }
    public String getLoginEmail() { return loginEmail; }
    public String getHandle() { return handle; }
    public String getName() { return name; }
    public CreatorRole getCreatorRole() { return creatorRole; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public List<ActivityField> getActivityFields() { return activityFields.stream().sorted().toList(); }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public int getExperienceRank() { return experienceRank; }
    public List<ActiveRegion> getActiveRegions() { return activeRegions.stream().sorted().toList(); }
    public List<TeamExperience> getTeamExperiences() { return teamExperiences.stream().sorted().toList(); }
    public int getTotalSlotCount() { return totalSlotCount; }
    public int getAvailableSlotCount() { return availableSlotCount; }
    public String getContact() { return contact; }
    public String getSns() { return sns; }
    public String getTools() { return tools; }
    public String getTimezone() { return timezone; }
    public String getCountryCode() { return countryCode; }
    public List<CareerEntryInfo> getCareers() { return careers.stream().map(this::toCareerInfo).toList(); }
    public boolean isActive() { return active; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedLoginEmail() { return deletedLoginEmail; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    private void assertActive() {
        if (!active) {
            throw new MemberException(MemberErrorCode.MEMBER_DEACTIVATED, id);
        }
    }

    private void validateCareerPeriod(LocalDate startDate, LocalDate endDate, boolean ongoing) {
        if (ongoing && endDate != null) {
            throw new MemberException(MemberErrorCode.INVALID_CAREER_PERIOD, "연재중 상태에서는 종료일을 입력할 수 없습니다");
        }
        if (!ongoing && endDate == null) {
            throw new MemberException(MemberErrorCode.INVALID_CAREER_PERIOD, "종료일 누락");
        }
        if (!ongoing && endDate.isBefore(startDate)) {
            throw new MemberException(MemberErrorCode.INVALID_CAREER_PERIOD,
                    startDate + " ~ " + endDate);
        }
    }

    private CareerEntryInfo toCareerInfo(CareerEntry entry) {
        return new CareerEntryInfo(
                entry.getId(), entry.getWorkTitle(), entry.getRole(),
                entry.getStartDate(), entry.getEndDate(), entry.isOngoing(), entry.getDescription(),
                entry.periodDisplay());
    }
}
