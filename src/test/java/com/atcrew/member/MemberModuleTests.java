package com.atcrew.member;

import com.atcrew.member.exception.MemberException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Testcontainers
class MemberModuleTests {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    MemberService memberService;

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
    void 중복_이메일_가입_불가() {
        memberService.register("dup@atcrew.com", "handle1", "회원A", CreatorRole.ILLUSTRATOR);

        assertThatThrownBy(() ->
                memberService.register("dup@atcrew.com", "handle2", "회원B", CreatorRole.ILLUSTRATOR)
        ).isInstanceOf(MemberException.class)
                .hasMessageContaining("이미 가입된 이메일");
    }

    @Test
    void 회원_탈퇴_후_비활성화() {
        MemberInfo member = memberService.register("leave@atcrew.com", "leavehandle", "탈퇴회원", CreatorRole.OTHER);

        memberService.deactivate(member.id());

        MemberInfo deactivated = memberService.findById(member.id());
        assertThat(deactivated.active()).isFalse();
        assertThat(deactivated.deletedAt()).isNotNull();
    }
}
