package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.JobSeekingPostStatus;
import com.atcrew.recruit.internal.domain.JobSeekingPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

// 검색(RecruitSearchQueryRepository)은 조건 조합이 동적이라 Specification으로 조회한다.
public interface JobSeekingPostRepository extends JpaRepository<JobSeekingPost, String>,
        JpaSpecificationExecutor<JobSeekingPost> {

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
}
