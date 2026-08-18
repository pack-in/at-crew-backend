package com.atcrew.portfolio.internal.persistence;

import com.atcrew.portfolio.PortfolioKind;
import com.atcrew.portfolio.ReflectionType;
import com.atcrew.portfolio.internal.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

// 내 포트폴리오 목록은 kind/reflectionType 선택 필터와 정렬 3종 조합이라 파생 쿼리 대신 Specification을 쓴다.
public interface PortfolioRepository extends JpaRepository<Portfolio, String>, JpaSpecificationExecutor<Portfolio> {

    // 작가 페이지 lazy 생성(getOrCreateArtistPage)용 — kind=ARTIST_PAGE는 uk_pf_owner_artist_page로
    // 회원당 1건이 보장된다. SHARED로 호출하면 여러 건이 나올 수 있으므로 쓰지 않는다.
    Optional<Portfolio> findByOwnerMemberIdAndKind(String ownerMemberId, PortfolioKind kind);

    // 작품 업로드·수정 화면의 선택 목록용 — 고정형은 구성이 얼어붙으므로 LIVE만 조회한다(§4).
    List<Portfolio> findByOwnerMemberIdAndReflectionTypeOrderByCreatedAtAsc(String ownerMemberId,
                                                                            ReflectionType reflectionType);

    // 탈퇴 시 소유 포트폴리오 전체(공유 + 작가 페이지)를 차단하기 위한 조회(§5.2).
    List<Portfolio> findByOwnerMemberId(String ownerMemberId);

    Optional<Portfolio> findByShareSlug(String shareSlug);

    // 슬러그 발급 시 충돌 재시도 판정용(§2.6).
    boolean existsByShareSlug(String shareSlug);
}
