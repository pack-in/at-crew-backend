package com.atcrew.member;

import com.atcrew.SharedContainersConfig;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import com.atcrew.common.response.CursorPage;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ImportTestcontainers(SharedContainersConfig.class)
@ExtendWith(DatabaseCleanupExtension.class)
class MemberModuleTests {

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
                3, 2, null, null, null, null, null, null, null, null, null, null, null, null, "010-9999-0000", null, null, null, null);

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
        memberService.updateInfo(available.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.THREE_TO_FOUR));

        MemberInfo preparing = memberService.register(
                "search-preparing@atcrew.com", "searchpreparing", "준비중작가");
        // PREPARING(기본값) 유지 — updateInfo 호출 없음

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE, EmploymentStatus.NEGOTIABLE), null, null, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(available.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(preparing.id());
    }

    @Test
    void 프로필_검색_언어_세그먼트_필터() {
        MemberInfo koCreator = registerWithPrimaryLanguage("lang-ko", "언어작가KO", Language.KO);
        MemberInfo jaCreator = registerWithPrimaryLanguage("lang-ja", "언어작가JA", Language.JA);
        // 마이그레이션 이전 회원 재현 — 주 사용 언어 없이 가입한 계정
        MemberInfo legacyCreator = memberService.register(
                "lang-legacy@atcrew.com", "langlegacy", "언어없는작가");
        for (MemberInfo creator : List.of(koCreator, jaCreator, legacyCreator)) {
            memberService.updateInfo(creator.id(), exposedProfile(
                    EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.THREE_TO_FOUR));
        }

        CursorPage<MemberProfileInfo> koViewer = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, null, List.of(Language.KO), null, 50));

        assertThat(koViewer.items()).extracting(MemberProfileInfo::id)
                .contains(koCreator.id(), legacyCreator.id())
                .doesNotContain(jaCreator.id());

        // 비로그인(빈 목록)은 필터 미적용
        CursorPage<MemberProfileInfo> anonymous = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, null, List.of(), null, 50));
        assertThat(anonymous.items()).extracting(MemberProfileInfo::id)
                .contains(koCreator.id(), jaCreator.id(), legacyCreator.id());
    }

    @Test
    void 게시물_언어_변경_후_계정_정보에_반영된다() {
        MemberInfo member = registerWithPrimaryLanguage("account-lang", "계정언어", Language.KO);

        memberService.updatePostLanguages(member.id(), java.util.Set.of(Language.KO, Language.EN));

        AccountInfo account = memberService.getAccount(member.id());
        assertThat(account.primaryLanguage()).isEqualTo(Language.KO);
        assertThat(account.postLanguages()).containsExactlyInAnyOrder(Language.KO, Language.EN);
        assertThat(memberService.findPostLanguages(member.id()))
                .containsExactlyInAnyOrder(Language.KO, Language.EN);
    }

    @Test
    void 주_사용_언어를_빼고_게시물_언어를_바꾸면_예외() {
        MemberInfo member = registerWithPrimaryLanguage("account-lang-fail", "계정언어실패", Language.KO);

        assertThatThrownBy(() -> memberService.updatePostLanguages(member.id(), java.util.Set.of(Language.JA)))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.PRIMARY_LANGUAGE_CANNOT_BE_REMOVED.name());
    }

    private MemberInfo registerWithPrimaryLanguage(String emailPrefix, String name, Language primaryLanguage) {
        return memberService.register(new RegisterMemberCommand(
                emailPrefix + "@atcrew.com", name, AuthProvider.EMAIL, "Secure1!",
                true, true, true, false, "Asia/Seoul", "KR", primaryLanguage));
    }

    @Test
    void 프로필_검색_활동분야_필터() {
        MemberInfo webtoon = memberService.register(
                "search-webtoon@atcrew.com", "searchwebtoon", "웹툰작가");
        memberService.updateInfo(webtoon.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.THREE_TO_FOUR));

        MemberInfo illustration = memberService.register(
                "search-illust@atcrew.com", "searchillust", "일러스트작가");
        memberService.updateInfo(illustration.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.ILLUSTRATION, ExperienceLevel.THREE_TO_FOUR));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), ActivityField.WEBTOON, null, null, null, 20));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(webtoon.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(illustration.id());
    }

    @Test
    void 프로필_검색_경력순_정렬() {
        MemberInfo newcomer = memberService.register(
                "search-exp-newcomer@atcrew.com", "searchexpnew", "신입작가");
        memberService.updateInfo(newcomer.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.NEWCOMER));

        MemberInfo senior = memberService.register(
                "search-exp-senior@atcrew.com", "searchexpsenior", "시니어작가");
        memberService.updateInfo(senior.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.TEN_PLUS));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, ProfileSort.EXPERIENCE, null, null, 20));

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
        memberService.updateInfo(popular.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.ONE_TO_TWO));
        MemberInfo quiet = memberService.register("sort-quiet@atcrew.com", "sortquiet", "조용작가");
        memberService.updateInfo(quiet.id(), exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.ONE_TO_TWO));

        MemberInfo v1 = memberService.register("sort-v1@atcrew.com", "sortvone", "조회자1");
        MemberInfo v2 = memberService.register("sort-v2@atcrew.com", "sortvtwo", "조회자2");
        memberService.findProfileByHandle("sortpopular", v1.id());
        memberService.findProfileByHandle("sortpopular", v2.id());
        memberService.findProfileByHandle("sortquiet", v1.id());

        awaitViewCount("sortpopular", 2);
        awaitViewCount("sortquiet", 1);

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, ProfileSort.VIEW_COUNT, null, null, 50));
        List<String> ids = result.items().stream().map(MemberProfileInfo::id).toList();
        assertThat(ids.indexOf(popular.id())).isLessThan(ids.indexOf(quiet.id()));
    }

    /**
     * 기획서 마이페이지_작가-R08 — 구인 가능 상태라도 노출 대상 항목이 비어 있으면 작가 찾기에 나오지 않는다.
     * 활동 분야는 복수 선택이라 "빈 컬렉션"이 미입력이다.
     */
    @Test
    void 프로필_검색_노출조건_미충족_회원_제외() {
        MemberInfo complete = registerWith("search-complete", "완성작가",
                exposedProfile(EmploymentStatus.AVAILABLE, ActivityField.WEBTOON, ExperienceLevel.ONE_TO_TWO));

        // 각 회원은 노출 조건 중 딱 하나씩만 비운다 — 그 항목 때문에 빠지는지 격리해서 확인한다.
        // (사용자 이름은 가입·수정 API가 공백을 막아 미입력 상태에 도달할 수 없어 제외한다.)
        MemberInfo noField = registerWith("search-nofield", "분야미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(), ExperienceLevel.ONE_TO_TWO,
                        List.of(DesiredRole.STORYBOARD), List.of(DesiredGenre.BL),
                        List.of(DesiredEmploymentType.FREELANCE), "010-1234-5678"));

        MemberInfo noLevel = registerWith("search-nolevel", "경력미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), null,
                        List.of(DesiredRole.STORYBOARD), List.of(DesiredGenre.BL),
                        List.of(DesiredEmploymentType.FREELANCE), "010-1234-5678"));

        MemberInfo noRole = registerWith("search-norole", "담당업무미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                        List.of(), List.of(DesiredGenre.BL),
                        List.of(DesiredEmploymentType.FREELANCE), "010-1234-5678"));

        MemberInfo noGenre = registerWith("search-nogenre", "장르미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                        List.of(DesiredRole.STORYBOARD), List.of(),
                        List.of(DesiredEmploymentType.FREELANCE), "010-1234-5678"));

        MemberInfo noEmploymentType = registerWith("search-notype", "채용형태미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                        List.of(DesiredRole.STORYBOARD), List.of(DesiredGenre.BL),
                        List.of(), "010-1234-5678"));

        MemberInfo noContact = registerWith("search-nocontact", "연락처미입력",
                exposure(EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON), ExperienceLevel.ONE_TO_TWO,
                        List.of(DesiredRole.STORYBOARD), List.of(DesiredGenre.BL),
                        List.of(DesiredEmploymentType.FREELANCE), null));

        CursorPage<MemberProfileInfo> result = memberService.searchProfiles(new SearchProfilesCommand(
                List.of(EmploymentStatus.AVAILABLE), null, null, null, null, 50));

        assertThat(result.items()).extracting(MemberProfileInfo::id).contains(complete.id());
        assertThat(result.items()).extracting(MemberProfileInfo::id).doesNotContain(
                noField.id(), noLevel.id(), noRole.id(), noGenre.id(), noEmploymentType.id(), noContact.id());
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

    /**
     * 작가 찾기 노출 조건(마이페이지_작가-R08) 7개 항목을 모두 채운 커맨드.
     * 조건이 늘어나도 이 헬퍼만 고치면 되도록 한 곳에 모아 둔다.
     */
    private UpdateInfoCommand exposedProfile(EmploymentStatus status, ActivityField field, ExperienceLevel level) {
        return exposure(status, List.of(field), level, List.of(DesiredRole.STORYBOARD),
                List.of(DesiredGenre.BL), List.of(DesiredEmploymentType.FREELANCE), "010-1234-5678");
    }

    // ─── 구직 정보 탭·직접입력 (마이페이지_작가-R24, 업로드-R13) ─────────

    @Test
    void 구직_정보_저장_후_조회() {
        MemberInfo member = memberService.register("js-save@atcrew.com", "jssave", "구직정보");
        memberService.updateInfo(member.id(), new UpdateInfoCommand(
                null, null, null, null, null, null, null,
                List.of(DrawingStyle.FEMALE_ORIENTED, DrawingStyle.CLEAN_LINE), WorkPace.QUALITY_FIRST,
                AvailableStartPeriod.WITHIN_TWO_WEEKS,
                List.of(DesiredRole.STORYBOARD, DesiredRole.LETTERING),
                List.of(DesiredGenre.BL, DesiredGenre.FANTASY),
                List.of(DesiredEmploymentType.FREELANCE, DesiredEmploymentType.NEGOTIABLE),
                DesiredWorkLocation.REMOTE,
                List.of(FeedbackPreference.SPECIFIC, FeedbackPreference.GENTLE),
                DesiredMinimumGuarantee.FROM_30_TO_40, DesiredAnnualSalary.FROM_3000_TO_3500,
                null, null, null, null, null, null));

        MemberInfo updated = memberService.findById(member.id());
        assertThat(updated.drawingStyles()).containsExactly(DrawingStyle.FEMALE_ORIENTED, DrawingStyle.CLEAN_LINE);
        assertThat(updated.workPace()).isEqualTo(WorkPace.QUALITY_FIRST);
        assertThat(updated.availableStartPeriod()).isEqualTo(AvailableStartPeriod.WITHIN_TWO_WEEKS);
        assertThat(updated.desiredRoles()).containsExactly(DesiredRole.STORYBOARD, DesiredRole.LETTERING);
        assertThat(updated.desiredGenres()).containsExactly(DesiredGenre.BL, DesiredGenre.FANTASY);
        assertThat(updated.desiredEmploymentTypes())
                .containsExactly(DesiredEmploymentType.FREELANCE, DesiredEmploymentType.NEGOTIABLE);
        assertThat(updated.desiredWorkLocation()).isEqualTo(DesiredWorkLocation.REMOTE);
        assertThat(updated.feedbackPreferences())
                .containsExactly(FeedbackPreference.SPECIFIC, FeedbackPreference.GENTLE);
        assertThat(updated.desiredMinimumGuarantee()).isEqualTo(DesiredMinimumGuarantee.FROM_30_TO_40);
        assertThat(updated.desiredAnnualSalary()).isEqualTo(DesiredAnnualSalary.FROM_3000_TO_3500);
    }

    @Test
    void 직접입력_앞뒤공백_제거하고_공백만이면_저장하지_않는다() {
        MemberInfo member = memberService.register("tag-trim@atcrew.com", "tagtrim", "태그정리");
        memberService.updateInfo(member.id(), customTags(List.of(
                new CustomTagInfo(CustomTagType.DESIRED_ROLE, "  배경작업  "),
                new CustomTagInfo(CustomTagType.DESIRED_ROLE, "   "),
                new CustomTagInfo(CustomTagType.DRAWING_STYLE, "수채화풍"))));

        assertThat(memberService.findById(member.id()).customTags())
                .containsExactly(
                        new CustomTagInfo(CustomTagType.DRAWING_STYLE, "수채화풍"),
                        new CustomTagInfo(CustomTagType.DESIRED_ROLE, "배경작업"));
    }

    @Test
    void 직접입력_같은_항목_중복은_한_번만_저장된다() {
        MemberInfo member = memberService.register("tag-dup@atcrew.com", "tagdup", "태그중복");
        memberService.updateInfo(member.id(), customTags(List.of(
                new CustomTagInfo(CustomTagType.DESIRED_GENRE, "느와르풍"),
                new CustomTagInfo(CustomTagType.DESIRED_GENRE, "느와르풍"))));

        assertThat(memberService.findById(member.id()).customTags()).hasSize(1);
    }

    @Test
    void 직접입력_10자_초과는_거부된다() {
        MemberInfo member = memberService.register("tag-long@atcrew.com", "taglong", "태그길이");

        assertThatThrownBy(() -> memberService.updateInfo(member.id(),
                customTags(List.of(new CustomTagInfo(CustomTagType.DRAWING_STYLE, "가".repeat(11))))))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_CUSTOM_TAG.name());
    }

    private UpdateInfoCommand customTags(List<CustomTagInfo> tags) {
        return new UpdateInfoCommand(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, tags, null, null, null, null, null);
    }

    private MemberInfo registerWith(String slug, String name, UpdateInfoCommand command) {
        MemberInfo member = memberService.register(slug + "@atcrew.com", slug.replace("-", ""), name);
        memberService.updateInfo(member.id(), command);
        return member;
    }

    /** 노출 조건 7개 항목을 개별로 지정한다 — 한 항목만 비워 그 항목 때문에 빠지는지 확인할 때 쓴다. */
    private UpdateInfoCommand exposure(EmploymentStatus status, List<ActivityField> fields, ExperienceLevel level,
                                       List<DesiredRole> roles, List<DesiredGenre> genres,
                                       List<DesiredEmploymentType> types, String contact) {
        return new UpdateInfoCommand(
                status, fields, level, null, null, null, null,
                null, null,
                null, roles, genres, types, null, null, null, null, null,
                contact, null, null, null, null);
    }
}
