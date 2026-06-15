package com.atcrew.member.internal.persistence;

import com.atcrew.member.AuthProvider;
import com.atcrew.member.internal.domain.Member;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {

    Optional<Member> findByHandle(String handle);

    boolean existsByHandle(String handle);

    // (loginEmail, authProvider) 복합 키 조회
    Optional<Member> findByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);

    boolean existsByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);
}
