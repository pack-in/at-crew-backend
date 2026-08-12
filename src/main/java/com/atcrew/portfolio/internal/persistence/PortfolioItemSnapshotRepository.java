package com.atcrew.portfolio.internal.persistence;

import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PortfolioItemSnapshotRepository extends JpaRepository<PortfolioItemSnapshot, Long> {

    List<PortfolioItemSnapshot> findByPortfolioIdOrderByOrdinal(String portfolioId);

    // 공유 열람 작품 목록의 첫 페이지 — 커서가 없을 때 쓴다.
    List<PortfolioItemSnapshot> findByPortfolioIdOrderByOrdinal(String portfolioId, Pageable pageable);

    // 공유 열람 작품 목록의 다음 페이지 — 정렬 기준이 ordinal 하나뿐이라 커서도 ordinal 단일값이다.
    List<PortfolioItemSnapshot> findByPortfolioIdAndOrdinalGreaterThanOrderByOrdinal(String portfolioId, int ordinal,
                                                                                     Pageable pageable);

    // 포트폴리오 삭제 시 스냅샷 행 정리 — PortfolioItemRepository.deleteByPortfolioId와 동일하게
    // 즉시 실행되는 벌크 DML로 둔다.
    @Modifying(flushAutomatically = true)
    @Query("delete from PortfolioItemSnapshot s where s.portfolioId = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") String portfolioId);
}
