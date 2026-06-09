package com.atcrew.member.internal.domain;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "members")
public class Member {

    @Id
    private String id;

    // 탈퇴 시 null로 클리어해 재가입 충돌 방지
    @Indexed(unique = true, sparse = true)
    private String loginEmail;

    @Indexed(unique = true, sparse = true)
    private String handle;

    private String name;
    private CreatorRole creatorRole;

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
    private LocalDateTime deletedAt;

    // 탈퇴 시 loginEmail 백업 (감사 추적용)
    private String deletedLoginEmail;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    protected Member() {
    }

    private Member(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        this.loginEmail = loginEmail;
        this.handle = handle;
        this.name = name;
        this.creatorRole = creatorRole;
    }

    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public void updateProfile(String name, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus,
                              List<ActivityField> activityFields,
                              ExperienceLevel experienceLevel,
                              List<ActiveRegion> activeRegions,
                              int totalSlotCount, int availableSlotCount,
                              List<TeamExperience> teamExperiences) {
        if (name != null) this.name = name;
        if (creatorRole != null) this.creatorRole = creatorRole;
        if (employmentStatus != null) this.employmentStatus = employmentStatus;
        if (activityFields != null) this.activityFields = new ArrayList<>(activityFields);
        if (experienceLevel != null) this.experienceLevel = experienceLevel;
        if (activeRegions != null) this.activeRegions = new ArrayList<>(activeRegions);
        this.totalSlotCount = totalSlotCount;
        this.availableSlotCount = availableSlotCount;
        if (teamExperiences != null) this.teamExperiences = new ArrayList<>(teamExperiences);
    }

    public void updateDetails(String contact, String sns, String tools) {
        if (contact != null) this.contact = contact;
        if (sns != null) this.sns = sns;
        if (tools != null) this.tools = tools;
    }

    public CareerEntryInfo addCareer(String workTitle, String role, String startDate,
                                     String endDate, boolean ongoing, String description) {
        CareerEntry entry = new CareerEntry(UUID.randomUUID().toString(), workTitle, role,
                startDate, endDate, ongoing, description);
        this.careers.add(entry);
        return toCareerInfo(entry);
    }

    public void updateCareer(String careerId, String workTitle, String role,
                             String startDate, String endDate, boolean ongoing, String description) {
        for (int i = 0; i < careers.size(); i++) {
            if (careers.get(i).getId().equals(careerId)) {
                careers.set(i, new CareerEntry(careerId, workTitle, role,
                        startDate, endDate, ongoing, description));
                return;
            }
        }
        throw new MemberException(MemberErrorCode.CAREER_NOT_FOUND, careerId);
    }

    public void deleteCareer(String careerId) {
        boolean removed = careers.removeIf(c -> c.getId().equals(careerId));
        if (!removed) {
            throw new MemberException(MemberErrorCode.CAREER_NOT_FOUND, careerId);
        }
    }

    // unique sparse 인덱스 필드를 null로 클리어해 재가입 충돌 방지
    public void deactivate() {
        this.active = false;
        this.deletedAt = LocalDateTime.now();
        this.deletedLoginEmail = this.loginEmail;
        this.loginEmail = null;
        this.handle = null;
    }

    public String getId() { return id; }
    public String getLoginEmail() { return loginEmail; }
    public String getHandle() { return handle; }
    public String getName() { return name; }
    public CreatorRole getCreatorRole() { return creatorRole; }
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
    public List<CareerEntryInfo> getCareers() { return careers.stream().map(this::toCareerInfo).toList(); }
    public boolean isActive() { return active; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private CareerEntryInfo toCareerInfo(CareerEntry entry) {
        return new CareerEntryInfo(
                entry.getId(), entry.getWorkTitle(), entry.getRole(),
                entry.getStartDate(), entry.getEndDate(), entry.isOngoing(), entry.getDescription(),
                computePeriodDisplay(entry.getStartDate(), entry.getEndDate(), entry.isOngoing()));
    }

    private static String computePeriodDisplay(String startDate, String endDate, boolean ongoing) {
        if (ongoing || endDate == null) return startDate + " ~ 연재중";

        String[] s = startDate.split("\\.");
        String[] e = endDate.split("\\.");
        int months = (Integer.parseInt(e[0]) - Integer.parseInt(s[0])) * 12
                   + (Integer.parseInt(e[1]) - Integer.parseInt(s[1]));

        String duration;
        if (months <= 0) {
            duration = "하루";
        } else if (months < 12) {
            duration = "약 " + months + "개월";
        } else {
            int years = months / 12;
            int rem = months % 12;
            duration = rem == 0 ? "약 " + years + "년" : "약 " + years + "년 " + rem + "개월";
        }
        return startDate + " ~ " + endDate + " " + duration;
    }
}
