package com.atcrew.member;

import com.atcrew.common.response.CursorPage;

import java.util.List;
import java.util.Optional;

public interface MemberService {

    MemberInfo register(RegisterMemberCommand command);

    // 개발·테스트 전용 (Firebase 인증 없이 직접 가입)
    MemberInfo register(String loginEmail, String handle, String name, CreatorRole creatorRole);

    MemberProfileInfo findProfileByHandle(String handle);

    /**
     * 핸들로 회원 프로필을 조회하며, 조회자 회원 ID를 함께 받아 {@link ArtistProfileViewedEvent}를
     * 발행한다(본인 조회는 제외, docs/design/recruit-module-design.md §2.7).
     *
     * @param viewerMemberId 조회자 회원 ID. 비로그인 조회면 null
     */
    MemberProfileInfo findProfileByHandle(String handle, String viewerMemberId);

    MemberInfo findByHandle(String handle);

    /**
     * 주어진 handle을 가진(탈퇴 여부 무관) 회원이 존재하는지 확인한다. 포트폴리오 공유 slug 발급 시 충돌 검사용.
     * 탈퇴 시 회원의 handle은 null로 클리어되므로, 활성 회원의 handle만 "존재"로 판단한다 — 별도 필터 불필요.
     */
    boolean existsByHandle(String handle);

    MemberInfo findByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider);

    /**
     * 비밀번호 재설정 요청(§7)처럼 계정 존재 여부를 노출하면 안 되는 흐름에서 사용.
     * 활성 회원이 아니거나 없으면 empty — {@link #findByLoginEmailAndProvider}와 달리 예외를 던지지 않는다.
     */
    Optional<MemberInfo> findActiveByLoginEmailAndProviderOrEmpty(String loginEmail, AuthProvider authProvider);

    MemberInfo findById(String memberId);

    /**
     * 구인 가능 상태 창작자 프로필 검색. 커뮤니티 "작가 찾아보기" 탭에서 사용.
     * 탈퇴 회원은 결과에서 항상 제외된다.
     *
     * <p>구인 가능 상태여도 노출 대상 항목(사용자 이름·활동 분야·활동 경력·연락처)이 비어 있으면
     * 결과에 포함되지 않는다 — 기획서 마이페이지_작가-R08.
     */
    CursorPage<MemberProfileInfo> searchProfiles(SearchProfilesCommand command);

    /**
     * 주어진 회원 ID 중 이름 또는 핸들에 검색어가 포함된 활성 회원의 ID만 반환한다.
     * 후보 집합을 호출자가 한정해 넘기는 용도이며(recruit "관심 작가" 검색), 탈퇴 회원은 항상 제외된다.
     */
    List<String> findIdsByKeyword(List<String> memberIds, String keyword);

    /**
     * EMAIL 활성 회원의 비밀번호 검증. timing-safe 보장 (회원 부재 시 더미 BCrypt 수행).
     * MISMATCHED는 회원 부재와 비밀번호 오답을 의도적으로 합산 — 호출자는 구분 불가.
     * 일치 시 memberId를 함께 반환해 auth 모듈의 중복 DB 조회를 제거한다.
     */
    PasswordVerification verifyPassword(String loginEmail, String rawPassword);

    void changePassword(String memberId, String rawNewPassword);

    /** 마케팅 정보 수신 동의 변경(설정 화면 토글). 가입 시 받은 값을 이후에도 켜고 끌 수 있다. */
    void updateMarketingAgreement(String memberId, boolean agreed);

    /**
     * 성인 콘텐츠 표시 설정 변경(설정 화면 토글). 표시 설정만 저장하며,
     * 본인 인증 여부에 따른 실제 콘텐츠 접근 게이팅은 이 값과 별개다.
     */
    void updateAdultContentVisible(String memberId, boolean visible);

    void updateName(String memberId, String name);

    void updateInfo(String memberId, UpdateInfoCommand command);

    CareerEntryInfo addCareer(String memberId, AddCareerCommand command);

    void deleteCareer(String memberId, String careerId);

    MemberInfo recordLogin(String memberId);

    void deactivate(String memberId);
}
