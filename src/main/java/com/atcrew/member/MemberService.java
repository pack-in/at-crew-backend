package com.atcrew.member;

import com.atcrew.common.response.CursorPage;

public interface MemberService {

    MemberInfo register(RegisterMemberCommand command);

    // 개발·테스트 전용 (Firebase 인증 없이 직접 가입)
    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberProfileInfo findProfileByHandle(String handle);

    MemberInfo findByHandle(String handle);

    MemberInfo findByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider);

    MemberInfo findById(String memberId);

    /**
     * 구인 가능 상태 창작자 프로필 검색. 커뮤니티 "작가 찾아보기" 탭에서 사용.
     * 탈퇴 회원은 결과에서 항상 제외된다.
     */
    CursorPage<MemberProfileInfo> searchProfiles(SearchProfilesCommand command);

    /**
     * EMAIL 활성 회원의 비밀번호 검증. timing-safe 보장 (회원 부재 시 더미 BCrypt 수행).
     * MISMATCHED는 회원 부재와 비밀번호 오답을 의도적으로 합산 — 호출자는 구분 불가.
     * 일치 시 memberId를 함께 반환해 auth 모듈의 중복 DB 조회를 제거한다.
     */
    PasswordVerification verifyPassword(String loginEmail, String rawPassword);

    void changePassword(String memberId, String rawNewPassword);

    void updateName(String memberId, String name);

    void updateInfo(String memberId, UpdateInfoCommand command);

    CareerEntryInfo addCareer(String memberId, AddCareerCommand command);

    void deleteCareer(String memberId, String careerId);

    MemberInfo recordLogin(String memberId);

    void deactivate(String memberId);
}
