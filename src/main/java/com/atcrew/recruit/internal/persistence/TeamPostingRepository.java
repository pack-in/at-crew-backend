package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.TeamPosting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TeamPostingRepository extends JpaRepository<TeamPosting, String> {

    /**
     * 공개 목록 첫 페이지 — 끌어올리기 적용 중인 글을 상단에 고정한다(설계 §2.1.1).
     * 정렬 키는 (적용 중이면 boostedUntil, 아니면 EPOCH) 내림차순 → id 내림차순 2단이다.
     * 정렬 키가 표현식이라 인덱스로 커버되지 않는다 — 공개 글이 크게 늘어나면 끌어올리기 스트림과
     * 일반 스트림을 분리 조회하는 방식으로 최적화를 검토한다.
     */
    @Query("""
            SELECT p FROM TeamPosting p
            WHERE p.status = :status
            ORDER BY CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END DESC, p.id DESC
            """)
    List<TeamPosting> findPublishedFirstPage(@Param("status") TeamPostingStatus status, @Param("now") Instant now,
            @Param("epoch") Instant epoch, Pageable pageable);

    // 공개 목록 다음 페이지 — (정렬 키, id) 복합 커서 기준 keyset 페이지네이션
    @Query("""
            SELECT p FROM TeamPosting p
            WHERE p.status = :status
              AND (CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END < :cursorBoostSortAt
                   OR (CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END = :cursorBoostSortAt
                       AND p.id < :cursorId))
            ORDER BY CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END DESC, p.id DESC
            """)
    List<TeamPosting> findPublishedNextPage(@Param("status") TeamPostingStatus status, @Param("now") Instant now,
            @Param("epoch") Instant epoch, @Param("cursorBoostSortAt") Instant cursorBoostSortAt,
            @Param("cursorId") String cursorId, Pageable pageable);

    // 내 목록(DELETED 제외) 첫 페이지
    List<TeamPosting> findByAuthorMemberIdAndStatusNotOrderByIdDesc(
            String authorMemberId, TeamPostingStatus excludedStatus, Pageable pageable);

    // 내 목록(DELETED 제외) 다음 페이지
    List<TeamPosting> findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
            String authorMemberId, TeamPostingStatus excludedStatus, String cursorId, Pageable pageable);

    // 휴지통 목록 첫 페이지
    List<TeamPosting> findByAuthorMemberIdAndStatusOrderByIdDesc(
            String authorMemberId, TeamPostingStatus status, Pageable pageable);

    // 휴지통 목록 다음 페이지
    List<TeamPosting> findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
            String authorMemberId, TeamPostingStatus status, String cursorId, Pageable pageable);

    // 조회수 원자적 증가 (Mongo $inc 대체, 설계 §1 동시성 원칙)
    @Modifying
    @Query("UPDATE TeamPosting p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") String id);

    // 북마크 수 원자적 증가 — 북마크 기능은 이번 스코프 밖(추후 북마크 API 연동 시 사용)
    @Modifying
    @Query("UPDATE TeamPosting p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.id = :id")
    void incrementBookmarkCount(@Param("id") String id);
}
