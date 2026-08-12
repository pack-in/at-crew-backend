package com.atcrew.portfolio.internal.persistence;

import com.atcrew.portfolio.internal.domain.PortfolioItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findByPortfolioIdOrderByOrdinal(String portfolioId);

    // 공유 열람 작품 목록의 첫 페이지 — 커서가 없을 때 쓴다.
    List<PortfolioItem> findByPortfolioIdOrderByOrdinal(String portfolioId, Pageable pageable);

    // 공유 열람 작품 목록의 다음 페이지 — 정렬 기준이 ordinal 하나뿐이라 커서도 ordinal 단일값이다.
    List<PortfolioItem> findByPortfolioIdAndOrdinalGreaterThanOrderByOrdinal(String portfolioId, int ordinal,
                                                                            Pageable pageable);

    // 구성 교체 시 DELETE가 새 INSERT보다 먼저 실행돼야 (portfolio_id, ordinal) 유니크 제약에 걸리지 않는다.
    // 파생 삭제는 flush 순서상 INSERT 뒤로 밀리므로 즉시 실행되는 벌크 DML을 쓴다.
    @Modifying(flushAutomatically = true)
    @Query("delete from PortfolioItem i where i.portfolioId = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") String portfolioId);

    // 라이브 멤버십 재계산용 — 0이면 artworks.portfolio_included를 false로 되돌린다(§5.4).
    long countByArtworkId(String artworkId);

    boolean existsByPortfolioIdAndArtworkId(String portfolioId, String artworkId);
}
