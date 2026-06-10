package com.atcrew.member;

import com.atcrew.TestMongoConfig;
import com.atcrew.member.exception.MemberErrorCode;
import com.atcrew.member.exception.MemberException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Testcontainers
@Import(TestMongoConfig.class)
class MemberModuleTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

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
    void 회원_가입_후_이메일로_조회() {
        MemberInfo member = memberService.register("email-lookup@atcrew.com", "emaillookup", "이메일조회", CreatorRole.ILLUSTRATOR);

        MemberInfo found = memberService.findByLoginEmail("email-lookup@atcrew.com");

        assertThat(found.id()).isEqualTo(member.id());
    }

    @Test
    void 중복_이메일_가입_불가() {
        memberService.register("dup@atcrew.com", "handle1", "회원A", CreatorRole.ILLUSTRATOR);

        assertThatThrownBy(() ->
                memberService.register("dup@atcrew.com", "handle2", "회원B", CreatorRole.ILLUSTRATOR)
        ).isInstanceOf(MemberException.class)
                .extracting(e -> ((MemberException) e).getCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL.name());
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
    void 없는_이메일_조회_시_예외() {
        assertThatThrownBy(() -> memberService.findByLoginEmail("nobody@atcrew.com"))
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
                3, 2, null, "010-9999-0000", null, null);

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
    void 회원_탈퇴_후_비활성화() {
        MemberInfo member = memberService.register("leave@atcrew.com", "leavehandle", "탈퇴회원", CreatorRole.OTHER);

        memberService.deactivate(member.id());

        MemberInfo deactivated = memberService.findById(member.id());
        assertThat(deactivated.active()).isFalse();
        assertThat(deactivated.deletedAt()).isNotNull();
    }
}
