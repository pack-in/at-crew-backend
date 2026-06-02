package com.atcrew.member;

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
    private String loginEmail; // 로그인용 이메일 (변경 불가)

    @Column(unique = true)
    private String handle; // 프로필 고유 식별자 (라이트의 folioId)

    @Column(nullable = false)
    private String name;

    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreatorRole creatorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceLevel experienceLevel;

    // 개인 정보
    private String birthDate;
    private String school;
    private String location;

    // 연락처 (사용자가 프로필에서 설정)
    private String contactEmail;
    private String socialMediaLink;
    private String twitter;

    // 경력
    private String desiredField;
    private String creativeTools;
    private String career;

    @ElementCollection
    @CollectionTable(name = "member_keywords", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    // 계정 상태
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
        this.name = name;
        this.creatorRole = creatorRole;
        this.employmentStatus = EmploymentStatus.PREPARING;
        this.experienceLevel = ExperienceLevel.NEWCOMER;
        this.profileImage = "";
        this.birthDate = "";
        this.school = "";
        this.location = "";
        this.contactEmail = "";
        this.socialMediaLink = "";
        this.twitter = "";
        this.desiredField = "";
        this.creativeTools = "";
        this.career = "";
    }

    public static Member register(String loginEmail, String handle, String name, CreatorRole creatorRole) {
        return new Member(loginEmail, handle, name, creatorRole);
    }

    public void updateProfile(String name, String profileImage, CreatorRole creatorRole,
                              EmploymentStatus employmentStatus, ExperienceLevel experienceLevel) {
        if (name != null) this.name = name;
        if (profileImage != null) this.profileImage = profileImage;
        if (creatorRole != null) this.creatorRole = creatorRole;
        if (employmentStatus != null) this.employmentStatus = employmentStatus;
        if (experienceLevel != null) this.experienceLevel = experienceLevel;
    }

    public void updateDetails(String birthDate, String school, String location,
                              String contactEmail, String socialMediaLink, String twitter,
                              String desiredField, String creativeTools, String career,
                              List<String> keywords) {
        if (birthDate != null) this.birthDate = birthDate;
        if (school != null) this.school = school;
        if (location != null) this.location = location;
        if (contactEmail != null) this.contactEmail = contactEmail;
        if (socialMediaLink != null) this.socialMediaLink = socialMediaLink;
        if (twitter != null) this.twitter = twitter;
        if (desiredField != null) this.desiredField = desiredField;
        if (creativeTools != null) this.creativeTools = creativeTools;
        if (career != null) this.career = career;
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

    public String getDeletedLoginEmail() { return deletedLoginEmail; }
    public Long getId() { return id; }
    public String getLoginEmail() { return loginEmail; }
    public String getHandle() { return handle; }
    public String getName() { return name; }
    public String getProfileImage() { return profileImage; }
    public CreatorRole getCreatorRole() { return creatorRole; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public String getBirthDate() { return birthDate; }
    public String getSchool() { return school; }
    public String getLocation() { return location; }
    public String getContactEmail() { return contactEmail; }
    public String getSocialMediaLink() { return socialMediaLink; }
    public String getTwitter() { return twitter; }
    public String getDesiredField() { return desiredField; }
    public String getCreativeTools() { return creativeTools; }
    public String getCareer() { return career; }
    public List<String> getKeywords() { return List.copyOf(keywords); }
    public boolean isActive() { return active; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
