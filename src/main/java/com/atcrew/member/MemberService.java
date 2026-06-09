package com.atcrew.member;

import java.util.List;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    MemberInfo findById(String memberId);

    void updateProfile(String memberId, String name, String profileImage, CreatorRole creatorRole,
                       EmploymentStatus employmentStatus,
                       int totalSlotCount, int availableSlotCount,
                       List<TeamExperience> teamExperiences);

    void updateDetails(String memberId, String location, String contactEmail, String socialMediaLink,
                       String twitter, String creativeTools, List<String> keywords);

    CareerEntryInfo addCareer(String memberId, String workTitle, String episodeCount,
                              String startDate, String endDate, boolean ongoing, String description);

    void updateCareer(String memberId, String careerId, String workTitle, String episodeCount,
                      String startDate, String endDate, boolean ongoing, String description);

    void deleteCareer(String memberId, String careerId);

    void deactivate(String memberId);
}
