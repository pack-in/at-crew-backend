package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.JobApplication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, String> {

    // 지원자 목록 첫 페이지 — id(UUIDv7)는 지원 시각순 정렬과 동일하므로 id 기준 커서로 충분
    List<JobApplication> findByJobPostingIdOrderByIdDesc(String jobPostingId, Pageable pageable);

    // 지원자 목록 다음 페이지(커서 이후)
    List<JobApplication> findByJobPostingIdAndIdLessThanOrderByIdDesc(
            String jobPostingId, String cursorId, Pageable pageable);

    // 단건 조회 — 다른 구인글의 지원 ID를 지정한 요청을 막기 위해 구인글 ID를 조건에 포함한다
    Optional<JobApplication> findByIdAndJobPostingId(String id, String jobPostingId);
}
