package com.atcrew.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
class MemberModuleTests {

    @Autowired
    MemberService memberService;

    @Test
    void 회원_가입_후_핸들로_조회() {
        Member member = memberService.register("test@atcrew.com", "testhandle", "테스트", CreatorRole.WEBTOON);

        Member found = memberService.findByHandle("testhandle");

        assertThat(found.getId()).isEqualTo(member.getId());
        assertThat(found.getName()).isEqualTo("테스트");
        assertThat(found.getCreatorRole()).isEqualTo(CreatorRole.WEBTOON);
        assertThat(found.getEmploymentStatus()).isEqualTo(EmploymentStatus.PREPARING);
    }

    @Test
    void 중복_이메일_가입_불가() {
        memberService.register("dup@atcrew.com", "handle1", "회원A", CreatorRole.ILLUSTRATOR);

        assertThatThrownBy(() ->
                memberService.register("dup@atcrew.com", "handle2", "회원B", CreatorRole.ILLUSTRATOR)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 가입된 이메일");
    }

    @Test
    void 회원_탈퇴_후_비활성화() {
        Member member = memberService.register("leave@atcrew.com", "leavehandle", "탈퇴회원", CreatorRole.OTHER);

        memberService.deactivate(member.getId());

        Member deactivated = memberService.findById(member.getId());
        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.getDeletedAt()).isNotNull();
    }
}
