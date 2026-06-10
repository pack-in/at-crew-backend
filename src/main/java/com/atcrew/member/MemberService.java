package com.atcrew.member;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo registerViaOAuth(String loginEmail, String name);

    boolean existsByLoginEmail(String loginEmail);

    MemberProfileInfo findProfileByHandle(String handle);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    MemberInfo findById(String memberId);

    void updateName(String memberId, String name);

    void updateInfo(String memberId, UpdateInfoCommand command);

    CareerEntryInfo addCareer(String memberId, AddCareerCommand command);

    void deleteCareer(String memberId, String careerId);

    MemberInfo recordLogin(String memberId);

    void deactivate(String memberId);
}
