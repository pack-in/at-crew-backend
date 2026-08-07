package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 검색(RecruitSearchQueryRepository)은 조건 조합이 동적이라 Specification으로 조회한다.
public interface JobSeekingPostRepository extends JpaRepository<JobSeekingPost, String>,
        JpaSpecificationExecutor<JobSeekingPost> {

    /**
     * 이미지 처리완료 이벤트가 같은 구직글에 대해 동시에 여러 건 들어와도 READY 판정이 서로의 갱신을
     * 놓치지 않도록 부모 행에 비관적 락을 건다 — {@code RecruitMediaEventListener} 전용
     * (동시성 레이스 수정, docs/NEXT_STEPS.md "지금 바로 처리할 것" 0번).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM JobSeekingPost p WHERE p.id = :id")
    Optional<JobSeekingPost> findByIdForUpdate(@Param("id") String id);

    // 공개 목록(PUBLISHED) 첫 페이지 — id(UUIDv7)는 생성 시각순 정렬과 동일하므로 id 기준 커서로 충분
    List<JobSeekingPost> findByStatusOrderByIdDesc(JobSeekingPostStatus status, Pageable pageable);

    // 공개 목록 다음 페이지(커서 이후)
    List<JobSeekingPost> findByStatusAndIdLessThanOrderByIdDesc(
            JobSeekingPostStatus status, String cursorId, Pageable pageable);

    // 내 목록(DELETED 제외) 첫 페이지
    List<JobSeekingPost> findByAuthorMemberIdAndStatusNotOrderByIdDesc(
            String authorMemberId, JobSeekingPostStatus excludedStatus, Pageable pageable);

    // 내 목록(DELETED 제외) 다음 페이지
    List<JobSeekingPost> findByAuthorMemberIdAndStatusNotAndIdLessThanOrderByIdDesc(
            String authorMemberId, JobSeekingPostStatus excludedStatus, String cursorId, Pageable pageable);

    // 휴지통 목록 첫 페이지
    List<JobSeekingPost> findByAuthorMemberIdAndStatusOrderByIdDesc(
            String authorMemberId, JobSeekingPostStatus status, Pageable pageable);

    // 휴지통 목록 다음 페이지
    List<JobSeekingPost> findByAuthorMemberIdAndStatusAndIdLessThanOrderByIdDesc(
            String authorMemberId, JobSeekingPostStatus status, String cursorId, Pageable pageable);

    // getForReindex(JOB_SEEKING) — 생성순 오름차순, 커서 있음/없음
    List<JobSeekingPost> findByCreatedAtAfterOrderByCreatedAtAsc(Instant cursor, Pageable pageable);

    List<JobSeekingPost> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
