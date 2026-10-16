package com.atcrew.member.internal.persistence;

import com.atcrew.member.internal.domain.MemberHandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// findById·existsById는 PK(utf8mb4_unicode_ci) 조회라 대소문자 무관이다.
public interface MemberHandleRepository extends JpaRepository<MemberHandle, String> {

    /** 회원이 소유한 handle 전부(현재·옛 handle). 등록 순. */
    List<MemberHandle> findAllByMemberIdOrderByCreatedAtAsc(String memberId);
}
