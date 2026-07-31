package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.internal.domain.JobPosting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, String> {

    // 공개 목록(PUBLISHED)·관리자 PENDING 목록 첫 페이지 — id(UUIDv7)는 생성 시각순 정렬과 동일하므로 id 기준 커서로 충분
    List<JobPosting> findByStatusOrderByIdDesc(JobPostingStatus status, Pageable pageable);

    // 공개 목록·관리자 PENDING 목록 다음 페이지(커서 이후)
    List<JobPosting> findByStatusAndIdLessThanOrderByIdDesc(JobPostingStatus status, String cursorId, Pageable pageable);

    // 내 목록(DELETED 제외) 첫 페이지
    List<JobPosting> findByAuthorMemberIdAndStatusNotOrderByIdDesc(
            String authorMemberId, JobPostingStatus excludedStatus, Pageable pageable);

    // 내 목록(DELETED 제외) 다음 페이지
    List<JobPosting> findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
            String authorMemberId, JobPostingStatus excludedStatus, String cursorId, Pageable pageable);

    // 휴지통 목록 첫 페이지
    List<JobPosting> findByAuthorMemberIdAndStatusOrderByIdDesc(
            String authorMemberId, JobPostingStatus status, Pageable pageable);

    // 휴지통 목록 다음 페이지
    List<JobPosting> findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
            String authorMemberId, JobPostingStatus status, String cursorId, Pageable pageable);

    // 조회수 원자적 증가 (Mongo $inc 대체, 설계 §1 동시성 원칙)
    @Modifying
    @Query("UPDATE JobPosting p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") String id);

    // 북마크 수 원자적 증가 — 북마크 기능은 이번 스코프 밖(추후 북마크 API 연동 시 사용)
    @Modifying
    @Query("UPDATE JobPosting p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.id = :id")
    void incrementBookmarkCount(@Param("id") String id);
}
