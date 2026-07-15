package com.atcrew.member.internal.domain;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "members")
public class Member {

    @Id
    private String id;

    // 탈퇴 시 null로 클리어 — 복합 unique 인덱스(loginEmail, authProvider)로 재가입 충돌 방지
    private String loginEmail;

    @Indexed(unique = true, sparse = true)
    private String handle;

    private String name;
    private CreatorRole creatorRole;

    private AuthProvider authProvider;
    private TermsAgreement termsAgreement;

    // EMAIL provider 전용. GOOGLE 회원·마이그레이션 미전환 회원은 null.
    // 절대 MemberInfo 등 공개 레코드로 노출하지 않는다.
    private String passwordHash;

    // 기본 false. Google 가입 시 true로 초기화. 이메일 인증 캠페인 대비 선제 추가.
    private boolean emailVerified = false;

    // IANA tz ID(예: "Asia/Tokyo", "America/New_York"). 가입 시 클라이언트 자동감지값을 저장,
    // 설정에서 변경 가능. UTC 오프셋이 아닌 ID로 저장해야 DST가 자동 반영된다.
    private String timezone;

    private EmploymentStatus employmentStatus = EmploymentStatus.PREPARING;
    private List<ActivityField> activityFields = new ArrayList<>();
    private ExperienceLevel experienceLevel;
    private List<ActiveRegion> activeRegions = new ArrayList<>();
    private List<TeamExperience> teamExperiences = new ArrayList<>();

    private int totalSlotCount = 5;
    private int availableSlotCount = 5;

    private String contact;
    private String sns;
    private String tools;

    private List<CareerEntry> careers = new ArrayList<>();

    private boolean active = true;
    private Instant deletedAt;
    private Instant lastLoginAt;

    // 탈퇴 시 loginEmail 백업 (감사 추적용)
    @Indexed(sparse = true)
    private String deletedLoginEmail;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Member() {
    }

    private Member(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        this.loginEmail = loginEmail;
        this.handle = handle;
        this.name = name;
        this.creatorRole = creatorRole;
    }

    // 개발·테스트 전용 (authProvider 없이 직접 가입)
    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public static Member registerWithEmail(String loginEmail, String handle, String name,
                                           String passwordHash, TermsAgreement termsAgreement,
                                           String timezone) {
        validateTerms(termsAgreement);
        validateTimezone(timezone);
        Member m = new Member(loginEmail, handle, name, null);
        m.authProvider = AuthProvider.EMAIL;
        m.passwordHash = passwordHash;
        m.emailVerified = false;
        m.termsAgreement = termsAgreement;
        m.timezone = timezone;
        return m;
    }

    public static Member registerWithGoogle(String loginEmail, String handle, String name,
                                            TermsAgreement termsAgreement, String timezone) {
        validateTerms(termsAgreement);
        validateTimezone(timezone);
        Member m = new Member(loginEmail, handle, name, null);
        m.authProvider = AuthProvider.GOOGLE;
        m.emailVerified = true;
        m.termsAgreement = termsAgreement;
        m.timezone = timezone;
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
        if (command.activityFields() != null) this.activityFields = new ArrayList<>(command.activityFields());
        if (command.experienceLevel() != null) this.experienceLevel = command.experienceLevel();
        if (command.activeRegions() != null) this.activeRegions = new ArrayList<>(command.activeRegions());
        if (totalSlotCount != null) this.totalSlotCount = effectiveTotal;
        if (totalSlotCount != null || availableSlotCount != null) this.availableSlotCount = effectiveAvailable;
        if (command.teamExperiences() != null) this.teamExperiences = new ArrayList<>(command.teamExperiences());
        if (command.contact() != null) this.contact = command.contact();
        if (command.sns() != null) this.sns = command.sns();
        if (command.tools() != null) this.tools = command.tools();
        if (command.timezone() != null) {
            validateTimezone(command.timezone());
            this.timezone = command.timezone();
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
        CareerEntry entry = new CareerEntry(UUID.randomUUID().toString(), workTitle, role,
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

    public String getId() { return id; }
    public String getLoginEmail() { return loginEmail; }
    public String getHandle() { return handle; }
    public String getName() { return name; }
    public CreatorRole getCreatorRole() { return creatorRole; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public List<ActivityField> getActivityFields() { return List.copyOf(activityFields); }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public List<ActiveRegion> getActiveRegions() { return List.copyOf(activeRegions); }
    public List<TeamExperience> getTeamExperiences() { return List.copyOf(teamExperiences); }
    public int getTotalSlotCount() { return totalSlotCount; }
    public int getAvailableSlotCount() { return availableSlotCount; }
    public String getContact() { return contact; }
    public String getSns() { return sns; }
    public String getTools() { return tools; }
    public String getTimezone() { return timezone; }
    public List<CareerEntryInfo> getCareers() { return careers.stream().map(this::toCareerInfo).toList(); }
    public boolean isActive() { return active; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedLoginEmail() { return deletedLoginEmail; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

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
