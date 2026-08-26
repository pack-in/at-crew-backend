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

import java.time.Duration;
import java.time.Instant;
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
        MemberInfo member = memberService.register("test@atcrew.com", "testhandle", "테스트");

        MemberInfo found = memberService.findByHandle("testhandle");

        assertThat(found.id()).isEqualTo(member.id());
        assertThat(found.name()).isEqualTo("테스트");
        assertThat(found.employmentStatus()).isEqualTo(EmploymentStatus.PREPARING);
    }

    @Test
    void 회원_가입_후_ID로_조회() {
        MemberInfo member = memberService.register("email-lookup@atcrew.com", "emaillookup", "이메일조회");

        MemberInfo found = memberService.findById(member.id());

        assertThat(found.id()).isEqualTo(member.id());
        assertThat(found.name()).isEqualTo("이메일조회");
    }

    @Test
    void 중복_핸들_가입_불가_개발용_API() {
        memberService.register("dup-a@atcrew.com", "duphandle2", "회원A");

        assertThatThrownBy(() ->
                memberService.register("dup-b@atcrew.com", "duphandle2", "회원B")
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_HANDLE.name());
    }

    @Test
    void 중복_핸들_가입_불가() {
        memberService.register("member-a@atcrew.com", "duphandle", "회원A");

        assertThatThrownBy(() ->
                memberService.register("member-b@atcrew.com", "duphandle", "회원B")
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
        memberService.register("exists-handle@atcrew.com", "existshandle", "존재핸들회원");

        assertThat(memberService.existsByHandle("existshandle")).isTrue();
    }

    @Test
    void 존재하지_않는_핸들은_존재하지_않음으로_판단() {
        assertThat(memberService.existsByHandle("nosuchhandle")).isFalse();
    }

    @Test
    void 탈퇴_회원의_과거_핸들은_존재하지_않음으로_판단() {
        MemberInfo member = memberService.register("deactivated-handle@atcrew.com", "deactivatedhandle", "탈퇴예정회원");

        memberService.deactivate(member.id());

        // 탈퇴 시 handle이 null로 클리어되므로, 과거 핸들은 slug 충돌 검사에서 재사용 가능해야 한다.
        assertThat(memberService.existsByHandle("deactivatedhandle")).isFalse();
    }

    // ─── updateName ───────────────────────────────────────────────────

    @Test
    void 이름_수정() {
        MemberInfo member = memberService.register("name-update@atcrew.com", "nameupdate", "홍길동");

        memberService.updateName(member.id(), "홍길순");

        assertThat(memberService.findById(member.id()).name()).isEqualTo("홍길순");
    }

    // ─── updateInfo ───────────────────────────────────────────────────

    @Test
    void 정보_수정() {
        MemberInfo member = memberService.register("info-update@atcrew.com", "infoupdate", "정보수정");
        UpdateInfoCommand command = new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.ILLUSTRATION), null, null,
                3, 2, null, "010-9999-0000", null, null, null, null);

        memberService.updateInfo(member.id(), command);

        MemberInfo updated = memberService.findById(member.id());
        assertThat(updated.employmentStatus()).isEqualTo(EmploymentStatus.AVAILABLE);
        assertThat(updated.totalSlotCount()).isEqualTo(3);
        assertThat(updated.availableSlotCount()).isEqualTo(2);
        assertThat(updated.contact()).isEqualTo("010-9999-0000");
    }

    // ─── career ───────────────────────────────────────────────────────

    @Test
    void 경력_추가_후_조회() {
        MemberInfo member = memberService.register("career-add@atcrew.com", "careeradd", "경력추가");
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
        MemberInfo member = memberService.register("career-del@atcrew.com", "careerdel", "경력삭제");
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
        MemberInfo member = memberService.register("leave@atcrew.com", "leavehandle", "탈퇴회원");

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
                "search-available@atcrew.com", "searchavailable", "구인가능작가");
        memberService.updateInfo(available.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo preparing = memberService.register(
                "search-preparing@atcrew.com", "searchpreparing", "준비중작가");
        // PREPARING(기본값) 유지 — updateInfo 호출 없음

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE), null, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(available.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(preparing.id());
    }

    @Test
    void 프로필_검색_활동분야_필터() {
        MemberInfo webtoon = memberService.register(
                "search-webtoon@atcrew.com", "searchwebtoon", "웹툰작가");
        memberService.updateInfo(webtoon.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo illustration = memberService.register(
                "search-illust@atcrew.com", "searchillust", "일러스트작가");
        memberService.updateInfo(illustration.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.ILLUSTRATION), ExperienceLevel.THREE_TO_FOUR,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), ActivityField.WEBTOON, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(webtoon.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(illustration.id());
    }

    @Test
    void 프로필_검색_경력순_정렬() {
        MemberInfo newcomer = memberService.register(
                "search-exp-newcomer@atcrew.com", "searchexpnew", "신입작가");
        memberService.updateInfo(newcomer.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.NEWCOMER,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo senior = memberService.register(
                "search-exp-senior@atcrew.com", "searchexpsenior", "시니어작가");
        memberService.updateInfo(senior.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.TEN_PLUS,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, ProfileSort.EXPERIENCE, null, 20));

        List<String> ids = result.items().stream().map(MemberProfileInfo::id).toList();
        assertThat(ids.indexOf(senior.id())).isLessThan(ids.indexOf(newcomer.id()));
    }

    // ─── 프로필 열람수·조회순 (마이페이지_작가-R03, 홈-R02) ─────────────

    @Test
    void 프로필_열람수_동일_조회자_24시간_내_반복조회는_1회만_집계() {
        MemberInfo artist = memberService.register("view-artist@atcrew.com", "viewartist", "열람작가");
        MemberInfo viewer = memberService.register("view-viewer@atcrew.com", "viewviewer", "조회자");

        memberService.findProfileByHandle("viewartist", viewer.id());
        memberService.findProfileByHandle("viewartist", viewer.id());
        memberService.findProfileByHandle("viewartist", viewer.id());

        awaitViewCount("viewartist", 1);
    }

    @Test
    void 프로필_열람수_조회자가_다르면_각각_집계() {
        MemberInfo artist = memberService.register("view-artist2@atcrew.com", "viewartist2", "열람작가2");
        MemberInfo v1 = memberService.register("view-v1@atcrew.com", "viewvone", "조회자1");
        MemberInfo v2 = memberService.register("view-v2@atcrew.com", "viewvtwo", "조회자2");

        memberService.findProfileByHandle("viewartist2", v1.id());
        memberService.findProfileByHandle("viewartist2", v2.id());

        awaitViewCount("viewartist2", 2);
    }

    @Test
    void 프로필_열람수_비로그인_조회는_집계하지_않는다() {
        // 중복 제외 키가 정해지지 않아(홈-R14) 집계 대상에서 제외한다 — ProfileViewCounter 주석 참고.
        memberService.register("view-artist3@atcrew.com", "viewartist3", "열람작가3");

        memberService.findProfileByHandle("viewartist3", null);
        memberService.findProfileByHandle("viewartist3", null);

        assertViewCountStaysZero("viewartist3");
    }

    @Test
    void 프로필_열람수_본인_조회는_집계하지_않는다() {
        MemberInfo artist = memberService.register("view-self@atcrew.com", "viewself", "본인조회");

        memberService.findProfileByHandle("viewself", artist.id());

        assertViewCountStaysZero("viewself");
    }

    @Test
    void 프로필_검색_조회순_정렬() {
        MemberInfo popular = memberService.register("sort-popular@atcrew.com", "sortpopular", "인기작가");
        memberService.updateInfo(popular.id(), new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));
        MemberInfo quiet = memberService.register("sort-quiet@atcrew.com", "sortquiet", "조용작가");
        memberService.updateInfo(quiet.id(), new UpdateInfoCommand(
                EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo v1 = memberService.register("sort-v1@atcrew.com", "sortvone", "조회자1");
        MemberInfo v2 = memberService.register("sort-v2@atcrew.com", "sortvtwo", "조회자2");
        memberService.findProfileByHandle("sortpopular", v1.id());
        memberService.findProfileByHandle("sortpopular", v2.id());
        memberService.findProfileByHandle("sortquiet", v1.id());

        awaitViewCount("sortpopular", 2);
        awaitViewCount("sortquiet", 1);

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, ProfileSort.VIEW_COUNT, null, 50));
        List<String> ids = result.items().stream().map(MemberProfileInfo::id).toList();
        assertThat(ids.indexOf(popular.id())).isLessThan(ids.indexOf(quiet.id()));
    }

    /**
     * 기획서 마이페이지_작가-R08 — 구인 가능 상태라도 노출 대상 항목이 비어 있으면 작가 찾기에 나오지 않는다.
     * 활동 분야는 복수 선택이라 "빈 컬렉션"이 미입력이다.
     */
    @Test
    void 프로필_검색_노출조건_미충족_회원_제외() {
        MemberInfo complete = memberService.register(
                "search-complete@atcrew.com", "searchcomplete", "완성작가");
        memberService.updateInfo(complete.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo noActivityField = memberService.register(
                "search-nofield@atcrew.com", "searchnofield", "분야미입력작가");
        memberService.updateInfo(noActivityField.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        MemberInfo noContact = memberService.register(
                "search-nocontact@atcrew.com", "searchnocontact", "연락처미입력작가");
        memberService.updateInfo(noContact.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                null, null, null, null, null, null, null, null, null));

        MemberInfo noExperience = memberService.register(
                "search-noexp@atcrew.com", "searchnoexp", "경력미입력작가");
        memberService.updateInfo(noExperience.id(), new UpdateInfoCommand(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), null,
                null, null, null, null, "010-1234-5678", null, null, null, null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, null, null, 50));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(complete.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id)
                .doesNotContain(noActivityField.id(), noContact.id(), noExperience.id());
    }

    /** 열람수 집계는 AFTER_COMMIT 별도 트랜잭션이라 반영까지 폴링한다(BookmarkModuleTests와 동일 관용구). */
    private void awaitViewCount(String handle, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            if (memberService.findProfileByHandle(handle).profileViewCount() == expected) return;
            sleepBriefly();
        }
        throw new AssertionError("열람수 " + expected + " 반영 대기 시간 초과: "
                + memberService.findProfileByHandle(handle).profileViewCount());
    }

    /** 집계되지 않아야 하는 케이스 — 잠깐 기다린 뒤에도 0인지 확인한다(반영 지연과 구분하기 위함). */
    private void assertViewCountStaysZero(String handle) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (Instant.now().isBefore(deadline)) {
            assertThat(memberService.findProfileByHandle(handle).profileViewCount()).isZero();
            sleepBriefly();
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
