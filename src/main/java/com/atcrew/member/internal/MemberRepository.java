package com.atcrew.member.internal;

import com.atcrew.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginEmail(String loginEmail);

    Optional<Member> findByHandle(String handle);

    boolean existsByHandle(String handle);

    boolean existsByLoginEmail(String loginEmail);
}
