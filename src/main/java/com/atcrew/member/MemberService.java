package com.atcrew.member;

public interface MemberService {

    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmail(String loginEmail);

    MemberInfo findById(String memberId);

    void updateName(String memberId, String name);

    void updateInfo(String memberId, UpdateInfoCommand command);

    CareerEntryInfo addCareer(String memberId, AddCareerCommand command);

    void deleteCareer(String memberId, String careerId);

    void deactivate(String memberId);
}
