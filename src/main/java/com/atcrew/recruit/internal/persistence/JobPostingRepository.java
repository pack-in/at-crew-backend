package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.JobPostingStatus;
import com.atcrew.recruit.internal.domain.JobPosting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

// 검색(RecruitSearchQueryRepository)은 조건 조합이 동적이라 Specification으로 조회한다.
public interface JobPostingRepository extends JpaRepository<JobPosting, String>, JpaSpecificationExecutor<JobPosting> {

    // 기업 마이페이지 "구인글 업로드 카드" 진입점 판단 — 공개 중인 구인글 보유 여부(§6.2)
    boolean existsByAuthorMemberIdAndStatus(String authorMemberId, JobPostingStatus status);

    // 내 목록/휴지통/관리자 PENDING 목록 첫 페이지 — id(UUIDv7)는 생성 시각순 정렬과 동일하므로 id 기준 커서로 충분
    List<JobPosting> findByStatusOrderByIdDesc(JobPostingStatus status, Pageable pageable);

    // 관리자 PENDING 목록 다음 페이지(커서 이후)
    List<JobPosting> findByStatusAndIdLessThanOrderByIdDesc(JobPostingStatus status, String cursorId, Pageable pageable);

    /**
     * 공개 목록 첫 페이지 — 끌어올리기 적용 중인 글을 상단에 고정한다(설계 §2.1.1).
     * 정렬 키는 (적용 중이면 boostedUntil, 아니면 EPOCH) 내림차순 → id 내림차순 2단이다.
     * 정렬 키가 표현식이라 인덱스로 커버되지 않는다 — 공개 글이 크게 늘어나면 끌어올리기 스트림과
     * 일반 스트림을 분리 조회하는 방식으로 최적화를 검토한다.
     */
    @Query("""
            SELECT p FROM JobPosting p
            WHERE p.status = :status
            ORDER BY CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END DESC, p.id DESC
            """)
    List<JobPosting> findPublishedFirstPage(@Param("status") JobPostingStatus status, @Param("now") Instant now,
            @Param("epoch") Instant epoch, Pageable pageable);

    // 공개 목록 다음 페이지 — (정렬 키, id) 복합 커서 기준 keyset 페이지네이션
    @Query("""
            SELECT p FROM JobPosting p
            WHERE p.status = :status
              AND (CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END < :cursorBoostSortAt
                   OR (CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END = :cursorBoostSortAt
                       AND p.id < :cursorId))
            ORDER BY CASE WHEN p.boostedUntil > :now THEN p.boostedUntil ELSE :epoch END DESC, p.id DESC
            """)
    List<JobPosting> findPublishedNextPage(@Param("status") JobPostingStatus status, @Param("now") Instant now,
            @Param("epoch") Instant epoch, @Param("cursorBoostSortAt") Instant cursorBoostSortAt,
            @Param("cursorId") String cursorId, Pageable pageable);

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

    // getPostsForReindex(JOB_POSTING) — 생성순 오름차순, 커서 있음/없음
    List<JobPosting> findByCreatedAtAfterOrderByCreatedAtAsc(Instant cursor, Pageable pageable);

    List<JobPosting> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
