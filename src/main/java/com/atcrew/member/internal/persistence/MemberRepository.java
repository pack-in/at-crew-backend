package com.atcrew.member.internal.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MemberRepository extends MongoRepository<Member, String> {

    Optional<Member> findByLoginEmail(String loginEmail);

    Optional<Member> findByHandle(String handle);

    boolean existsByHandle(String handle);

    boolean existsByLoginEmail(String loginEmail);
}
