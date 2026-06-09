package com.atcrew.member;

import java.util.List;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    MemberInfo findById(String memberId);

    void updateProfile(String memberId, String name, CreatorRole creatorRole,
                       EmploymentStatus employmentStatus,
                       List<ActivityField> activityFields,
                       ExperienceLevel experienceLevel,
                       List<ActiveRegion> activeRegions,
                       int totalSlotCount, int availableSlotCount,
                       List<TeamExperience> teamExperiences);

    void updateDetails(String memberId, String contact, String sns, String tools);

    CareerEntryInfo addCareer(String memberId, String workTitle, String role,
                              String startDate, String endDate, boolean ongoing, String description);

    void updateCareer(String memberId, String careerId, String workTitle, String role,
                      String startDate, String endDate, boolean ongoing, String description);

    void deleteCareer(String memberId, String careerId);

    void deactivate(String memberId);
}
