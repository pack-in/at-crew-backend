package com.atcrew.member;

public interface MemberService {

    MemberInfo register(RegisterMemberCommand command);

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    boolean existsByLoginEmail(String loginEmail);

    boolean isDeactivatedEmail(String loginEmail);

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
