package com.atcrew.member.internal.persistence;

import com.atcrew.member.AuthProvider;
import com.atcrew.member.internal.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByHandle(String handle);

    boolean existsByHandle(String handle);

    // (loginEmail, authProvider) 복합 키 조회
    Optional<Member> findByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);

    boolean existsByLoginEmailAndAuthProvider(String loginEmail, AuthProvider authProvider);

    /**
     * 후보 ID 집합 안에서 이름·핸들 부분 일치 검색 — recruit "관심 작가" 검색에서 사용한다.
     * 탈퇴 회원은 제외하며, 검색어는 파라미터 바인딩으로만 전달한다.
     * 검색어의 LIKE 와일드카드(%, _)는 이스케이프하지 않는다 — 후보 집합이 호출자가 넘긴 ID로 한정돼
     * 조회 범위가 넓어지지 않기 때문이다.
     */
    @Query("""
            SELECT m.id FROM Member m
            WHERE m.id IN :memberIds
              AND m.active = true
              AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(m.handle) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    List<String> findIdsByKeyword(@Param("memberIds") List<String> memberIds, @Param("keyword") String keyword);

    /**
     * 프로필 열람수를 1 올린다. 읽고-쓰는 대신 DB 레벨 증분이라 동시 조회에서도 유실되지 않는다.
     * updatedAt은 건드리지 않는다 — 열람은 프로필 "수정"이 아니라서 업데이트순 정렬을 흔들면 안 된다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.profileViewCount = m.profileViewCount + 1 WHERE m.id = :id")
    int incrementProfileViewCount(@Param("id") String id);

    // Mongo findAndModify → 조건부 UPDATE + 영향 행 수 판별 (docs/design/mariadb-migration-design.md §3.3.1)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.lastLoginAt = :now, m.updatedAt = :now WHERE m.id = :id AND m.active = true")
    int recordLogin(String id, Instant now);
}
