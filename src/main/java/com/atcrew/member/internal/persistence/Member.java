package com.atcrew.member.internal.persistence;

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

    private MemberProfile profile;
    private MemberPersonalInfo personalInfo;
    private MemberContact contact;
    private MemberCareer careerInfo;

    private int totalSlotCount = 5;
    private int availableSlotCount = 5;
    private List<TeamExperience> teamExperiences = new ArrayList<>();
    private List<CareerEntry> careers = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();

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
        this.profile = new MemberProfile(name, "", creatorRole, EmploymentStatus.PREPARING, ExperienceLevel.NEWCOMER);
        this.personalInfo = new MemberPersonalInfo("", "", "");
        this.contact = new MemberContact("", "", "");
        this.careerInfo = new MemberCareer("", "");
    }

    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public void updateProfile(String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus,
                              int totalSlotCount, int availableSlotCount,
                              List<TeamExperience> teamExperiences) {
        MemberProfile updated = this.profile;
        if (name != null) updated = updated.withName(name);
        if (profileImage != null) updated = updated.withProfileImage(profileImage);
        if (creatorRole != null) updated = updated.withCreatorRole(creatorRole);
        if (employmentStatus != null) updated = updated.withEmploymentStatus(employmentStatus);
        this.profile = updated;
        this.totalSlotCount = totalSlotCount;
        this.availableSlotCount = availableSlotCount;
        if (teamExperiences != null) {
            this.teamExperiences = new ArrayList<>(teamExperiences);
        }
    }

    public void updateDetails(String location, String contactEmail, String socialMediaLink,
                              String twitter, String creativeTools, List<String> keywords) {
        if (location != null) this.personalInfo = this.personalInfo.withLocation(location);

        MemberContact updatedContact = this.contact;
        if (contactEmail != null) updatedContact = updatedContact.withContactEmail(contactEmail);
        if (socialMediaLink != null) updatedContact = updatedContact.withSocialMediaLink(socialMediaLink);
        if (twitter != null) updatedContact = updatedContact.withTwitter(twitter);
        this.contact = updatedContact;

        if (creativeTools != null) this.careerInfo = this.careerInfo.withCreativeTools(creativeTools);
        if (keywords != null) this.keywords = new ArrayList<>(keywords);
    }

    public CareerEntryInfo addCareer(String workTitle, String episodeCount, String startDate,
                                     String endDate, boolean ongoing, String description) {
        CareerEntry entry = new CareerEntry(UUID.randomUUID().toString(), workTitle, episodeCount,
                startDate, endDate, ongoing, description);
        this.careers.add(entry);
        return toCareerInfo(entry);
    }

    public void updateCareer(String careerId, String workTitle, String episodeCount,
                             String startDate, String endDate, boolean ongoing, String description) {
        for (int i = 0; i < careers.size(); i++) {
            if (careers.get(i).getId().equals(careerId)) {
                careers.set(i, new CareerEntry(careerId, workTitle, episodeCount,
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
    public boolean isActive() { return active; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getDeletedLoginEmail() { return deletedLoginEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getTotalSlotCount() { return totalSlotCount; }
    public int getAvailableSlotCount() { return availableSlotCount; }
    public List<TeamExperience> getTeamExperiences() { return List.copyOf(teamExperiences); }
    public List<CareerEntryInfo> getCareers() { return careers.stream().map(this::toCareerInfo).toList(); }
    public List<String> getKeywords() { return List.copyOf(keywords); }

    private CareerEntryInfo toCareerInfo(CareerEntry entry) {
        return new CareerEntryInfo(entry.getId(), entry.getWorkTitle(), entry.getEpisodeCount(),
                entry.getStartDate(), entry.getEndDate(), entry.isOngoing(), entry.getDescription());
    }

    public String getName() { return profile.getName(); }
    public String getProfileImage() { return profile.getProfileImage(); }
    public CreatorRole getCreatorRole() { return profile.getCreatorRole(); }
    public EmploymentStatus getEmploymentStatus() { return profile.getEmploymentStatus(); }
    public ExperienceLevel getExperienceLevel() { return profile.getExperienceLevel(); }

    public String getContactEmail() { return contact.getContactEmail(); }
    public String getSocialMediaLink() { return contact.getSocialMediaLink(); }
    public String getTwitter() { return contact.getTwitter(); }
    public String getCreativeTools() { return careerInfo.getCreativeTools(); }
    public String getDesiredField() { return careerInfo.getDesiredField(); }
    public String getBirthDate() { return personalInfo.getBirthDate(); }
    public String getSchool() { return personalInfo.getSchool(); }
    public String getLocation() { return personalInfo.getLocation(); }
}
