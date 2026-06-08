package com.atcrew.member.internal.persistence;

import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 탈퇴 시 null로 클리어해 재가입 충돌 방지 (unique + sparse)
    @Column(unique = true, updatable = false)
    private String loginEmail;

    @Column(unique = true)
    private String handle;

    @Embedded
    private MemberProfile profile;

    @Embedded
    private MemberPersonalInfo personalInfo;

    @Embedded
    private MemberContact contact;

    @Embedded
    private MemberCareer career;

    @ElementCollection
    @CollectionTable(name = "member_keywords", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime deletedAt;

    // 탈퇴 시 loginEmail 백업 (감사 추적용)
    private String deletedLoginEmail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Member() {
    }

    private Member(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        this.loginEmail = loginEmail;
        this.handle = handle;
        this.profile = new MemberProfile(name, "", creatorRole, EmploymentStatus.PREPARING, ExperienceLevel.NEWCOMER);
        this.personalInfo = new MemberPersonalInfo("", "", "");
        this.contact = new MemberContact("", "", "");
        this.career = new MemberCareer("", "", "");
    }

    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public void updateProfile(String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus, ExperienceLevel experienceLevel) {
        MemberProfile updated = this.profile;
        if (name != null) updated = updated.withName(name);
        if (profileImage != null) updated = updated.withProfileImage(profileImage);
        if (creatorRole != null) updated = updated.withCreatorRole(creatorRole);
        if (employmentStatus != null) updated = updated.withEmploymentStatus(employmentStatus);
        if (experienceLevel != null) updated = updated.withExperienceLevel(experienceLevel);
        this.profile = updated;
    }

    public void updateDetails(String birthDate, String school, String location,
                              String contactEmail, String socialMediaLink, String twitter,
                              String desiredField, String creativeTools, String careerText,
                              List<String> keywords) {
        MemberPersonalInfo updatedPersonalInfo = this.personalInfo;
        if (birthDate != null) updatedPersonalInfo = updatedPersonalInfo.withBirthDate(birthDate);
        if (school != null) updatedPersonalInfo = updatedPersonalInfo.withSchool(school);
        if (location != null) updatedPersonalInfo = updatedPersonalInfo.withLocation(location);
        this.personalInfo = updatedPersonalInfo;

        MemberContact updatedContact = this.contact;
        if (contactEmail != null) updatedContact = updatedContact.withContactEmail(contactEmail);
        if (socialMediaLink != null) updatedContact = updatedContact.withSocialMediaLink(socialMediaLink);
        if (twitter != null) updatedContact = updatedContact.withTwitter(twitter);
        this.contact = updatedContact;

        MemberCareer updatedCareer = this.career;
        if (desiredField != null) updatedCareer = updatedCareer.withDesiredField(desiredField);
        if (creativeTools != null) updatedCareer = updatedCareer.withCreativeTools(creativeTools);
        if (careerText != null) updatedCareer = updatedCareer.withCareer(careerText);
        this.career = updatedCareer;

        if (keywords != null) {
            this.keywords.clear();
            this.keywords.addAll(keywords);
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

    public Long getId() { return id; }
    public String getLoginEmail() { return loginEmail; }
    public String getHandle() { return handle; }
    public String getDeletedLoginEmail() { return deletedLoginEmail; }
    public boolean isActive() { return active; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<String> getKeywords() { return List.copyOf(keywords); }

    public String getName() { return profile.getName(); }
    public String getProfileImage() { return profile.getProfileImage(); }
    public CreatorRole getCreatorRole() { return profile.getCreatorRole(); }
    public EmploymentStatus getEmploymentStatus() { return profile.getEmploymentStatus(); }
    public ExperienceLevel getExperienceLevel() { return profile.getExperienceLevel(); }
}
