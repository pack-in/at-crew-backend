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

    // 원본 영구 삭제 반영용 — 여러 포트폴리오에 걸친 구성 행을 한 번에 지운다(§1.2).
    @Modifying(flushAutomatically = true)
    @Query("delete from PortfolioItem i where i.artworkId = :artworkId")
    void deleteByArtworkId(@Param("artworkId") String artworkId);

    // 원본 변경 이벤트로 재계산할 대상 포트폴리오 — 엔티티를 올리지 않고 ID만 읽는다(idx_pi_artwork 역조회).
    @Query("select distinct i.portfolioId from PortfolioItem i where i.artworkId = :artworkId")
    List<String> findPortfolioIdsByArtworkId(@Param("artworkId") String artworkId);

    // 6시간 주기 보정 대상 — 구성 행이 하나라도 남아 있는 포트폴리오만 훑는다(§5.5).
    @Query("select distinct i.portfolioId from PortfolioItem i")
    List<String> findDistinctPortfolioIds();

    // 라이브 멤버십 재계산용 — 0이면 artworks.portfolio_included를 false로 되돌린다(§5.4).
    // 열람이 막힌 포트폴리오(탈퇴·운영 차단)는 유효한 공개 위치가 아니므로 세지 않는다 — 세면 탈퇴 시
    // 해제한 편입 여부를 6시간 보정 배치가 도로 켜서 탈퇴 회원 작품이 다시 열린다(§5.2, §5.4).
    @Query("""
            select count(i) from PortfolioItem i
            where i.artworkId = :artworkId
              and exists (select 1 from Portfolio p where p.id = i.portfolioId and p.blockedAt is null)
            """)
    long countActiveByArtworkId(@Param("artworkId") String artworkId);

    boolean existsByPortfolioIdAndArtworkId(String portfolioId, String artworkId);
}
