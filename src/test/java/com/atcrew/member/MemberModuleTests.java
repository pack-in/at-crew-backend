package com.atcrew.member;

import com.atcrew.common.response.CursorPage;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Testcontainers
class MemberModuleTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @Autowired
    MemberService memberService;

    // ─── register ────────────────────────────────────────────────────

    @Test
    void 회원_가입_후_핸들로_조회() {
        MemberInfo member = memberService.register("test@atcrew.com", "testhandle", "테스트", CreatorRole.WEBTOON);

        MemberInfo found = memberService.findByHandle("testhandle");

        assertThat(found.id()).isEqualTo(member.id());
        assertThat(found.name()).isEqualTo("테스트");
        assertThat(found.creatorRole()).isEqualTo(CreatorRole.WEBTOON);
        assertThat(found.employmentStatus()).isEqualTo(EmploymentStatus.PREPARING);
    }

    @Test
    void 회원_가입_후_ID로_조회() {
        MemberInfo member = memberService.register("email-lookup@atcrew.com", "emaillookup", "이메일조회", CreatorRole.ILLUSTRATOR);

        MemberInfo found = memberService.findById(member.id());

        assertThat(found.id()).isEqualTo(member.id());
        assertThat(found.name()).isEqualTo("이메일조회");
    }

    @Test
    void 중복_핸들_가입_불가_개발용_API() {
        memberService.register("dup-a@atcrew.com", "duphandle2", "회원A", CreatorRole.ILLUSTRATOR);

        assertThatThrownBy(() ->
                memberService.register("dup-b@atcrew.com", "duphandle2", "회원B", CreatorRole.ILLUSTRATOR)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_HANDLE.name());
    }

    @Test
    void 중복_핸들_가입_불가() {
        memberService.register("member-a@atcrew.com", "duphandle", "회원A", CreatorRole.ILLUSTRATOR);

        assertThatThrownBy(() ->
                memberService.register("member-b@atcrew.com", "duphandle", "회원B", CreatorRole.ILLUSTRATOR)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_HANDLE.name());
    }

    // ─── find ─────────────────────────────────────────────────────────

    @Test
    void 없는_핸들_조회_시_예외() {
        assertThatThrownBy(() -> memberService.findByHandle("nonexistent"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.name());
    }

    @Test
    void 없는_ID_조회_시_예외() {
        assertThatThrownBy(() -> memberService.findById("000000000000000000000000"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND.name());
    }

    // ─── existsByHandle (portfolio 공유 slug 충돌 검사) ──────────────────

    @Test
    void 존재하는_핸들은_존재로_판단() {
        memberService.register("exists-handle@atcrew.com", "existshandle", "존재핸들회원", CreatorRole.WEBTOON);

        assertThat(memberService.existsByHandle("existshandle")).isTrue();
    }

    @Test
    void 존재하지_않는_핸들은_존재하지_않음으로_판단() {
        assertThat(memberService.existsByHandle("nosuchhandle")).isFalse();
    }

    @Test
    void 탈퇴_회원의_과거_핸들은_존재하지_않음으로_판단() {
        MemberInfo member = memberService.register("deactivated-handle@atcrew.com", "deactivatedhandle", "탈퇴예정회원", CreatorRole.WEBTOON);

        memberService.deactivate(member.id());

        // 탈퇴 시 handle이 null로 클리어되므로, 과거 핸들은 slug 충돌 검사에서 재사용 가능해야 한다.
        assertThat(memberService.existsByHandle("deactivatedhandle")).isFalse();
    }

    // ─── updateName ───────────────────────────────────────────────────

    @Test
    void 이름_수정() {
        MemberInfo member = memberService.register("name-update@atcrew.com", "nameupdate", "홍길동", CreatorRole.WEBTOON);

        memberService.updateName(member.id(), "홍길순");

        assertThat(memberService.findById(member.id()).name()).isEqualTo("홍길순");
    }

    // ─── updateInfo ───────────────────────────────────────────────────

    @Test
    void 정보_수정() {
        MemberInfo member = memberService.register("info-update@atcrew.com", "infoupdate", "정보수정", CreatorRole.WEBTOON);
        UpdateInfoCommand command = new UpdateInfoCommand(
                CreatorRole.ILLUSTRATOR, EmploymentStatus.AVAILABLE,
                List.of(ActivityField.ILLUSTRATION), null, null,
                3, 2, null, "010-9999-0000", null, null, null, null);

        memberService.updateInfo(member.id(), command);

        MemberInfo updated = memberService.findById(member.id());
        assertThat(updated.creatorRole()).isEqualTo(CreatorRole.ILLUSTRATOR);
        assertThat(updated.employmentStatus()).isEqualTo(EmploymentStatus.AVAILABLE);
        assertThat(updated.totalSlotCount()).isEqualTo(3);
        assertThat(updated.availableSlotCount()).isEqualTo(2);
        assertThat(updated.contact()).isEqualTo("010-9999-0000");
    }

    // ─── career ───────────────────────────────────────────────────────

    @Test
    void 경력_추가_후_조회() {
        MemberInfo member = memberService.register("career-add@atcrew.com", "careeradd", "경력추가", CreatorRole.WEBTOON);
        AddCareerCommand command = new AddCareerCommand(
                "홍길동전", "작화 전공정",
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 6, 30),
                false, "작화 담당");

        CareerEntryInfo entry = memberService.addCareer(member.id(), command);

        assertThat(entry.workTitle()).isEqualTo("홍길동전");
        assertThat(entry.periodDisplay()).contains("약 1년 5개월");

        MemberInfo found = memberService.findById(member.id());
        assertThat(found.careers()).hasSize(1);
        assertThat(found.careers().get(0).id()).isEqualTo(entry.id());
    }

    @Test
    void 경력_삭제() {
        MemberInfo member = memberService.register("career-del@atcrew.com", "careerdel", "경력삭제", CreatorRole.WEBTOON);
        CareerEntryInfo entry = memberService.addCareer(member.id(), new AddCareerCommand(
                "삭제할작품", null,
                LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1),
                false, null));

        memberService.deleteCareer(member.id(), entry.id());

        assertThat(memberService.findById(member.id()).careers()).isEmpty();
    }

    // ─── deactivate ───────────────────────────────────────────────────

    @Test
    void 회원_탈퇴_후_ID_조회_불가() {
        MemberInfo member = memberService.register("leave@atcrew.com", "leavehandle", "탈퇴회원", CreatorRole.OTHER);

        memberService.deactivate(member.id());

        // 탈퇴 후 findById는 MEMBER_DEACTIVATED (403) — refresh token 재발급 차단
        assertThatThrownBy(() -> memberService.findById(member.id()))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    // ─── searchProfiles (커뮤니티 작가 찾아보기) ─────────────────────────

    @Test
    void 프로필_검색_구인가능_상태만_노출() {
        MemberInfo available = memberService.register(
                "search-available@atcrew.com", "searchavailable", "구인가능작가", CreatorRole.WEBTOON);
        memberService.updateInfo(available.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo preparing = memberService.register(
                "search-preparing@atcrew.com", "searchpreparing", "준비중작가", CreatorRole.WEBTOON);
        // PREPARING(기본값) 유지 — updateInfo 호출 없음

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE), null, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(available.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(preparing.id());
    }

    @Test
    void 프로필_검색_활동분야_필터() {
        MemberInfo webtoon = memberService.register(
                "search-webtoon@atcrew.com", "searchwebtoon", "웹툰작가", CreatorRole.WEBTOON);
        memberService.updateInfo(webtoon.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo illustration = memberService.register(
                "search-illust@atcrew.com", "searchillust", "일러스트작가", CreatorRole.ILLUSTRATOR);
        memberService.updateInfo(illustration.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.ILLUSTRATION), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), ActivityField.WEBTOON, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(webtoon.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(illustration.id());
    }

    @Test
    void 프로필_검색_경력순_정렬() {
        MemberInfo newcomer = memberService.register(
                "search-exp-newcomer@atcrew.com", "searchexpnew", "신입작가", CreatorRole.WEBTOON);
        memberService.updateInfo(newcomer.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.NEWCOMER,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo senior = memberService.register(
                "search-exp-senior@atcrew.com", "searchexpsenior", "시니어작가", CreatorRole.WEBTOON);
        memberService.updateInfo(senior.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.TEN_PLUS,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, ProfileSort.EXPERIENCE, null, 20));

        List<String> ids = result.items().stream().map(MemberProfileInfo::id).toList();
        assertThat(ids.indexOf(senior.id())).isLessThan(ids.indexOf(newcomer.id()));
    }

    /**
     * 기획서 마이페이지_작가-R08 — 구인 가능 상태라도 노출 대상 항목이 비어 있으면 작가 찾기에 나오지 않는다.
     * 활동 분야는 복수 선택이라 "빈 컬렉션"이 미입력이다.
     */
    @Test
    void 프로필_검색_노출조건_미충족_회원_제외() {
        MemberInfo complete = memberService.register(
                "search-complete@atcrew.com", "searchcomplete", "완성작가", CreatorRole.WEBTOON);
        memberService.updateInfo(complete.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo noActivityField = memberService.register(
                "search-nofield@atcrew.com", "searchnofield", "분야미입력작가", CreatorRole.WEBTOON);
        memberService.updateInfo(noActivityField.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo noContact = memberService.register(
                "search-nocontact@atcrew.com", "searchnocontact", "연락처미입력작가", CreatorRole.WEBTOON);
        memberService.updateInfo(noContact.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, null, null, null, null, null));

        MemberInfo noExperience = memberService.register(
                "search-noexp@atcrew.com", "searchnoexp", "경력미입력작가", CreatorRole.WEBTOON);
        memberService.updateInfo(noExperience.id(), new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), null,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, null, null, 50));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(complete.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id)
                .doesNotContain(noActivityField.id(), noContact.id(), noExperience.id());
    }
}
