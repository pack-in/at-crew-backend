package com.atcrew.portfolio.internal.persistence;

import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PortfolioItemSnapshotRepository extends JpaRepository<PortfolioItemSnapshot, Long> {

    // 복제 후보 추출용 — 운영 차단 여부와 무관하게 생성 당시 구성을 그대로 읽는다.
    // 차단된 작품은 원본 상태로 자동 선택에서 걸러지되 "제외된 개수"에는 포함돼야 하기 때문이다(마이페이지_작가-R41).
    List<PortfolioItemSnapshot> findByPortfolioIdOrderByOrdinal(String portfolioId);

    // 카드 목록 — 운영 차단된 스냅샷은 외부 노출에서 제외한다(마이페이지_작가-R39).
    List<PortfolioItemSnapshot> findByPortfolioIdAndBlockedAtIsNullOrderByOrdinal(String portfolioId);

    // 공유 열람 작품 목록·카드 커버의 첫 페이지 — 커서가 없을 때 쓴다.
    List<PortfolioItemSnapshot> findByPortfolioIdAndBlockedAtIsNullOrderByOrdinal(String portfolioId,
                                                                                  Pageable pageable);

    // 공유 열람 작품 목록의 다음 페이지 — 정렬 기준이 ordinal 하나뿐이라 커서도 ordinal 단일값이다.
    List<PortfolioItemSnapshot> findByPortfolioIdAndBlockedAtIsNullAndOrdinalGreaterThanOrderByOrdinal(
            String portfolioId, int ordinal, Pageable pageable);

    // 카드 "N개" 표기 — 고정형은 생성 당시 스냅샷 수를 쓰되 차단된 스냅샷만 뺀다(마이페이지_작가-R39).
    long countByPortfolioIdAndBlockedAtIsNull(String portfolioId);

    // 스냅샷 상세 조회 — 식별자만으로 찾지 않고 포트폴리오와 짝을 맞춰 조회한다.
    // 타 포트폴리오의 스냅샷 식별자를 끼워 넣어도 열리지 않아야 하기 때문이다(마이페이지_작가-R39).
    Optional<PortfolioItemSnapshot> findByPortfolioIdAndSnapshotPublicId(String portfolioId,
                                                                         String snapshotPublicId);

    // R2 key 보존 판정(§5.6) — 후보 key가 카드 썸네일로 걸린 스냅샷을 찾는다. 상세 본문 이미지 key는
    // payload_json 안에 있어 SQL로 조회할 수 없으므로, 걸린 스냅샷의 payload를 호출자가 펼쳐 보존
    // 집합을 만든다. 포트폴리오 행이 남아있는(=삭제되지 않은) 스냅샷만 대상이다.
    @Query("""
            select s from PortfolioItemSnapshot s
            where (s.thumbKey in :keys or s.thumbAdultKey in :keys)
              and exists (select 1 from Portfolio p where p.id = s.portfolioId)
            """)
    List<PortfolioItemSnapshot> findActiveByThumbnailKeys(@Param("keys") Collection<String> keys);

    // 포트폴리오 삭제 시 스냅샷 행 정리 — PortfolioItemRepository.deleteByPortfolioId와 동일하게
    // 즉시 실행되는 벌크 DML로 둔다.
    @Modifying(flushAutomatically = true)
    @Query("delete from PortfolioItemSnapshot s where s.portfolioId = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") String portfolioId);
}
