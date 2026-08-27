package com.atcrew.member.internal.persistence;

import com.atcrew.member.internal.domain.ProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProfileViewRepository extends JpaRepository<ProfileView, ProfileView.Key> {

    /**
     * 직전 조회가 기준 시각보다 오래됐을 때만 조회 시각을 갱신한다.
     * 영향 행 수가 1이면 "24시간이 지난 재방문"이므로 열람수를 올려야 한다는 뜻이고,
     * 0이면 행이 없거나(최초 조회) 아직 24시간이 지나지 않은 반복 조회다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ProfileView v SET v.viewedAt = :now
            WHERE v.artistMemberId = :artistMemberId
              AND v.viewerMemberId = :viewerMemberId
              AND v.viewedAt < :staleBefore
            """)
    int touchIfStale(@Param("artistMemberId") String artistMemberId,
                     @Param("viewerMemberId") String viewerMemberId,
                     @Param("now") Instant now,
                     @Param("staleBefore") Instant staleBefore);
}
