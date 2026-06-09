package com.atcrew.member;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    MemberInfo findById(String memberId);

    void updateProfile(String memberId, UpdateProfileCommand command);

    void updateDetails(String memberId, String contact, String sns, String tools);

    CareerEntryInfo addCareer(String memberId, AddCareerCommand command);

    void updateCareer(String memberId, String careerId, UpdateCareerCommand command);

    void deleteCareer(String memberId, String careerId);

    void deactivate(String memberId);
}
