package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.JobPostingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobPostingImageRepository extends JpaRepository<JobPostingImage, Long> {

    List<JobPostingImage> findByPostingIdOrderByOrdinalAsc(String postingId);

    // 목록 조회 N+1 방지 — 한 번에 여러 게시글의 이미지를 읽는다.
    List<JobPostingImage> findByPostingIdInOrderByOrdinalAsc(Collection<String> postingIds);

    // 교체 시 DELETE가 새 INSERT보다 먼저 실행돼야 (posting_id, ordinal) 유니크 제약에 걸리지 않는다.
    // 파생 삭제는 flush 순서상 INSERT 뒤로 밀리므로 즉시 실행되는 벌크 DML을 쓴다.
    @Modifying(flushAutomatically = true)
    @Query("delete from JobPostingImage i where i.postingId = :postingId")
    void deleteByPostingId(@Param("postingId") String postingId);
}
