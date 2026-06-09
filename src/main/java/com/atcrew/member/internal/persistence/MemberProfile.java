package com.atcrew.member.internal.persistence;

import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;

class MemberProfile {

    private String name;
    private String profileImage;
    private CreatorRole creatorRole;
    private EmploymentStatus employmentStatus;
    private ExperienceLevel experienceLevel;

    protected MemberProfile() {
    }

    MemberProfile(String name, String profileImage, CreatorRole creatorRole,
                  EmploymentStatus employmentStatus, ExperienceLevel experienceLevel) {
        this.name = name;
        this.profileImage = profileImage;
        this.creatorRole = creatorRole;
        this.employmentStatus = employmentStatus;
        this.experienceLevel = experienceLevel;
    }

    MemberProfile withName(String name) {
        return new MemberProfile(name, this.profileImage, this.creatorRole, this.employmentStatus, this.experienceLevel);
    }

    MemberProfile withProfileImage(String profileImage) {
        return new MemberProfile(this.name, profileImage, this.creatorRole, this.employmentStatus, this.experienceLevel);
    }

    MemberProfile withCreatorRole(CreatorRole creatorRole) {
        return new MemberProfile(this.name, this.profileImage, creatorRole, this.employmentStatus, this.experienceLevel);
    }

    MemberProfile withEmploymentStatus(EmploymentStatus employmentStatus) {
        return new MemberProfile(this.name, this.profileImage, this.creatorRole, employmentStatus, this.experienceLevel);
    }

    MemberProfile withExperienceLevel(ExperienceLevel experienceLevel) {
        return new MemberProfile(this.name, this.profileImage, this.creatorRole, this.employmentStatus, experienceLevel);
    }

    String getName() { return name; }
    String getProfileImage() { return profileImage; }
    CreatorRole getCreatorRole() { return creatorRole; }
    EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    ExperienceLevel getExperienceLevel() { return experienceLevel; }
}
