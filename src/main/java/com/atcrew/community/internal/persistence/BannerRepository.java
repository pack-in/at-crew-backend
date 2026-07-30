package com.atcrew.community.internal.persistence;

import com.atcrew.community.BannerStatus;
import com.atcrew.community.internal.domain.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BannerRepository extends JpaRepository<Banner, String> {

    List<Banner> findByStatusOrderBySortOrderAsc(BannerStatus status);

    Optional<Banner> findFirstByOrderBySortOrderDesc();

    // 지정 순번(from) 이후 배너를 전부 한 칸 뒤로 민다 — 신규 등록 시 자리를 만든다 (자기 자신은 아직 미저장이라 제외 불필요).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Banner b SET b.sortOrder = b.sortOrder + 1, b.updatedAt = :now WHERE b.sortOrder >= :from")
    void shiftFromInclusive(int from, Instant now);

    // 배너를 앞으로 당길 때 [from, to) 구간의 다른 배너들을 한 칸씩 뒤로 민다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Banner b SET b.sortOrder = b.sortOrder + 1, b.updatedAt = :now " +
           "WHERE b.id <> :excludeId AND b.sortOrder >= :from AND b.sortOrder < :to")
    void shiftRangeForward(String excludeId, int from, int to, Instant now);

    // 배너를 뒤로 밀 때 (from, to] 구간의 다른 배너들을 한 칸씩 앞으로 당긴다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Banner b SET b.sortOrder = b.sortOrder - 1, b.updatedAt = :now " +
           "WHERE b.id <> :excludeId AND b.sortOrder > :from AND b.sortOrder <= :to")
    void shiftRangeBackward(String excludeId, int from, int to, Instant now);
}
