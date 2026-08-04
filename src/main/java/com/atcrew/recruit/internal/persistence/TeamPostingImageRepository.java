package com.atcrew.recruit.internal.persistence;

import com.atcrew.recruit.internal.domain.TeamPostingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TeamPostingImageRepository extends JpaRepository<TeamPostingImage, Long> {

    List<TeamPostingImage> findByPostingIdOrderByOrdinalAsc(String postingId);

    // 목록 조회 N+1 방지 — 한 번에 여러 게시글의 이미지를 읽는다.
    List<TeamPostingImage> findByPostingIdInOrderByOrdinalAsc(Collection<String> postingIds);

    // 교체 시 DELETE가 새 INSERT보다 먼저 실행돼야 (posting_id, ordinal) 유니크 제약에 걸리지 않는다.
    @Modifying(flushAutomatically = true)
    @Query("delete from TeamPostingImage i where i.postingId = :postingId")
    void deleteByPostingId(@Param("postingId") String postingId);
}
