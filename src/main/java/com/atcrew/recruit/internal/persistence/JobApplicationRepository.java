package com.atcrew.recruit.internal.persistence;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 게시글 영구 삭제 시 함께 지운다(#200) — 자식 행이라 게시글 행보다 먼저 지워야 외래키에 걸리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from JobApplication a where a.jobPostingId = :postingId")
    void deleteByJobPostingId(@Param("postingId") String postingId);

}
