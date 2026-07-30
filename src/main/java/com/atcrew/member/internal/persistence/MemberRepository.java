package com.atcrew.member.internal.persistence;

import com.atcrew.member.AuthProvider;
import com.atcrew.member.internal.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByHandle(String handle);

    boolean existsByHandle(String handle);

    // (loginEmail, authProvider) 복합 키 조회
    Optional<Member> findByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);

    boolean existsByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);

    // Mongo findAndModify → 조건부 UPDATE + 영향 행 수 판별 (docs/design/mariadb-migration-design.md §3.3.1)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.lastLoginAt = :now, m.updatedAt = :now WHERE m.id = :id AND m.active = true")
    int recordLogin(String id, Instant now);
}
