package com.atcrew.member.internal.domain;

import com.atcrew.member.ActivityField;
import com.atcrew.member.AuthProvider;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.UpdateInfoCommand;
import com.atcrew.member.internal.exception.MemberErrorCode;
import com.atcrew.member.internal.exception.MemberException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    Member member;

    @BeforeEach
    void setUp() {
        member = Member.register("test@atcrew.com", "testhandle", "홍길동", CreatorRole.WEBTOON);
    }

    // ─── addCareer ────────────────────────────────────────────────────

    @Test
    void 경력_정상_추가() {
        CareerEntryInfo entry = member.addCareer("홍길동전", "작화",
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 6, 30), false, "작화 전공정");

        assertThat(entry.workTitle()).isEqualTo("홍길동전");
        assertThat(entry.ongoing()).isFalse();
        assertThat(entry.id()).isNotNull();
        assertThat(member.getCareers()).hasSize(1);
    }

    @Test
    void 연재중_경력에_종료일_있으면_예외() {
        assertThatThrownBy(() ->
                member.addCareer("홍길동전", "작화",
                        LocalDate.of(2023, 1, 1), LocalDate.of(2024, 6, 30), true, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_CAREER_PERIOD.name());
    }

    @Test
    void 종료_경력에_종료일_없으면_예외() {
        assertThatThrownBy(() ->
                member.addCareer("홍길동전", "작화",
                        LocalDate.of(2023, 1, 1), null, false, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_CAREER_PERIOD.name());
    }

    @Test
    void 종료일이_시작일보다_이전이면_예외() {
        assertThatThrownBy(() ->
                member.addCareer("홍길동전", "작화",
                        LocalDate.of(2024, 6, 1), LocalDate.of(2024, 1, 1), false, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_CAREER_PERIOD.name());
    }

    @Test
    void 경력_50개_초과_시_예외() {
        for (int i = 0; i < 50; i++) {
            member.addCareer("작품" + i, null,
                    LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false, null);
        }

        assertThatThrownBy(() ->
                member.addCareer("초과작품", null,
                        LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.CAREER_LIMIT_EXCEEDED.name());
    }

    // ─── deleteCareer ─────────────────────────────────────────────────

    @Test
    void 경력_정상_삭제() {
        CareerEntryInfo entry = member.addCareer("홍길동전", "작화",
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1), false, null);

        member.deleteCareer(entry.id());

        assertThat(member.getCareers()).isEmpty();
    }

    @Test
    void 없는_경력_삭제_시_예외() {
        assertThatThrownBy(() -> member.deleteCareer("nonexistent-id"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.CAREER_NOT_FOUND.name());
    }

    // ─── updateInfo ───────────────────────────────────────────────────

    @Test
    void 작업가능슬롯이_전체슬롯_초과_시_예외() {
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, 3, 4, null, null, null, null, null);

        assertThatThrownBy(() -> member.updateInfo(command))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_SLOT_COUNT.name());
    }

    @Test
    void 전체슬롯만_줄이면_가능슬롯_자동_조정() {
        // 기본값: totalSlotCount=5, availableSlotCount=5
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, 2, null, null, null, null, null, null);

        member.updateInfo(command);

        assertThat(member.getTotalSlotCount()).isEqualTo(2);
        assertThat(member.getAvailableSlotCount()).isEqualTo(2); // 5 → 2로 자동 조정
    }

    @Test
    void 기존_전체슬롯_기준_가능슬롯_초과_시_예외() {
        // totalSlotCount 기본값 5, availableSlotCount만 6으로 설정하면 초과
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, null, 6, null, null, null, null, null);

        assertThatThrownBy(() -> member.updateInfo(command))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_SLOT_COUNT.name());
    }

    @Test
    void 정보_정상_업데이트() {
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, List.of(ActivityField.WEBTOON, ActivityField.ILLUSTRATION),
                null, null, 3, 2, null, "010-1234-5678", null, null, null);

        member.updateInfo(command);

        assertThat(member.getTotalSlotCount()).isEqualTo(3);
        assertThat(member.getAvailableSlotCount()).isEqualTo(2);
        assertThat(member.getEmploymentStatus()).isEqualTo(EmploymentStatus.AVAILABLE);
        assertThat(member.getActivityFields()).containsExactlyInAnyOrder(ActivityField.WEBTOON, ActivityField.ILLUSTRATION);
        assertThat(member.getContact()).isEqualTo("010-1234-5678");
    }

    // ─── recordLogin ─────────────────────────────────────────────────

    @Test
    void 탈퇴_회원_로그인_기록_시_예외() {
        member.deactivate();

        assertThatThrownBy(() -> member.recordLogin())
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    // ─── deactivate ───────────────────────────────────────────────────

    @Test
    void 탈퇴_시_이메일_핸들_null로_클리어() {
        member.deactivate();

        assertThat(member.getLoginEmail()).isNull();
        assertThat(member.getHandle()).isNull();
        assertThat(member.isActive()).isFalse();
        assertThat(member.getDeletedAt()).isNotNull();
    }

    @Test
    void 탈퇴_후_이름_수정_시_예외() {
        member.deactivate();

        assertThatThrownBy(() -> member.updateName("새이름"))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    @Test
    void 탈퇴_후_정보_수정_시_예외() {
        member.deactivate();
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, EmploymentStatus.AVAILABLE, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> member.updateInfo(command))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    @Test
    void 탈퇴_후_경력_추가_시_예외() {
        member.deactivate();

        assertThatThrownBy(() ->
                member.addCareer("작품", null,
                        LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1), false, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    @Test
    void 이미_탈퇴한_회원_재탈퇴_시_예외() {
        member.deactivate();

        assertThatThrownBy(() -> member.deactivate())
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.MEMBER_DEACTIVATED.name());
    }

    // ─── periodDisplay ────────────────────────────────────────────────

    @Test
    void 기간표시_연재중() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2023, 1, 15), null, true, null);

        assertThat(entry.periodDisplay()).isEqualTo("2023.01.15 ~ 연재중");
    }

    @Test
    void 기간표시_하루() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 2), false, null);

        assertThat(entry.periodDisplay()).contains("하루");
    }

    @Test
    void 기간표시_1개월_미만_일수() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 15), false, null);

        assertThat(entry.periodDisplay()).contains("14일");
    }

    @Test
    void 기간표시_개월() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 1), false, null);

        assertThat(entry.periodDisplay()).contains("약 5개월");
    }

    @Test
    void 기간표시_정확히_1년() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1), false, null);

        assertThat(entry.periodDisplay()).contains("약 1년");
        assertThat(entry.periodDisplay()).doesNotContain("개월");
    }

    @Test
    void 기간표시_1년_이상_나머지_개월() {
        CareerEntryInfo entry = member.addCareer("작품", null,
                LocalDate.of(2022, 1, 1), LocalDate.of(2023, 6, 1), false, null);

        assertThat(entry.periodDisplay()).contains("약 1년 5개월");
    }

    // ─── register (신규 가입 팩토리) ──────────────────────────────────

    @Test
    void 이메일_가입() {
        TermsAgreement terms = TermsAgreement.of(true, true, true, false);
        Member creator = Member.registerWithEmail("creator@test.com", "creatorhandle", "창작자",
                "$2a$10$dummyhash", terms, "Asia/Seoul");

        assertThat(creator.getAuthProvider()).isEqualTo(AuthProvider.EMAIL);
        assertThat(creator.getName()).isEqualTo("창작자");
        assertThat(creator.hasPassword()).isTrue();
        assertThat(creator.getTimezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void Google_가입() {
        TermsAgreement terms = TermsAgreement.of(true, true, true, false);
        Member creator = Member.registerWithGoogle("creator@test.com", "creatorhandle", "창작자", terms, "Asia/Seoul");

        assertThat(creator.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(creator.hasPassword()).isFalse();
        assertThat(creator.getTimezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 유효하지_않은_시간대로_가입_시_예외() {
        TermsAgreement terms = TermsAgreement.of(true, true, true, false);

        assertThatThrownBy(() ->
                Member.registerWithEmail("creator@test.com", "handle", "창작자", "hash", terms, "Not/A_Zone")
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_TIMEZONE.name());
    }

    @Test
    void 시간대_null로_가입_시_예외() {
        TermsAgreement terms = TermsAgreement.of(true, true, true, false);

        assertThatThrownBy(() ->
                Member.registerWithGoogle("creator@test.com", "handle", "창작자", terms, null)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_TIMEZONE.name());
    }

    @Test
    void updateInfo로_시간대_변경() {
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, null, null, null, null, null, null, "America/New_York");

        member.updateInfo(command);

        assertThat(member.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    void updateInfo에_유효하지_않은_시간대_시_예외() {
        UpdateInfoCommand command = new UpdateInfoCommand(
                null, null, null, null, null, null, null, null, null, null, null, "Not/A_Zone");

        assertThatThrownBy(() -> member.updateInfo(command))
                .isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.INVALID_TIMEZONE.name());
    }

    @Test
    void 필수_약관_미동의_시_예외() {
        TermsAgreement terms = TermsAgreement.of(false, true, true, false);

        assertThatThrownBy(() ->
                Member.registerWithEmail("creator@test.com", "handle", "창작자", "hash", terms, "Asia/Seoul")
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.TERMS_NOT_AGREED.name());
    }

    @Test
    void 제3자제공_미동의_시_예외() {
        TermsAgreement terms = TermsAgreement.of(true, true, false, false);

        assertThatThrownBy(() ->
                Member.registerWithGoogle("creator@test.com", "handle", "창작자", terms, "Asia/Seoul")
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.TERMS_NOT_AGREED.name());
    }

    @Test
    void 탈퇴_시_passwordHash_클리어() {
        TermsAgreement terms = TermsAgreement.of(true, true, true, false);
        Member creator = Member.registerWithEmail("creator@test.com", "creatorhandle", "창작자",
                "$2a$10$dummyhash", terms, "Asia/Seoul");

        creator.deactivate();

        assertThat(creator.hasPassword()).isFalse();
    }
}
