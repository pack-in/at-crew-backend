package com.atcrew.member;

import java.util.List;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    void updateProfile(Long memberId, String name, String profileImage, CreatorRole creatorRole,
                       EmploymentStatus employmentStatus, ExperienceLevel experienceLevel);

    void updateDetails(Long memberId, String birthDate, String school, String location,
                       String contactEmail, String socialMediaLink, String twitter,
                       String desiredField, String creativeTools, String career,
                       List<String> keywords);

    void deactivate(Long memberId);

    MemberInfo findById(Long memberId);
}
