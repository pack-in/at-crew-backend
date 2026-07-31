package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.TeamPosting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamPostingRepository extends JpaRepository<TeamPosting, String> {

    // 공개 목록(PUBLISHED) 첫 페이지 — id(UUIDv7)는 생성 시각순 정렬과 동일하므로 id 기준 커서로 충분
    List<TeamPosting> findByStatusOrderByIdDesc(TeamPostingStatus status, Pageable pageable);

    // 공개 목록 다음 페이지(커서 이후)
    List<TeamPosting> findByStatusAndIdLessThanOrderByIdDesc(TeamPostingStatus status, String cursorId, Pageable pageable);

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
