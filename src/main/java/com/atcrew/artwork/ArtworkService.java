package com.atcrew.artwork;

import com.atcrew.common.response.CursorPage;
import com.atcrew.member.Language;

import java.util.List;
import java.util.Optional;

public interface ArtworkService {

    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes);

    ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command);

    ArtworkInfo getArtwork(String artworkId, String viewerMemberId);

    ArtworkStatus getArtworkStatus(String memberId, String artworkId);

    ArtworkInfo updateArtwork(String memberId, String artworkId, UpdateArtworkCommand command);

    /**
     * 노출 위치 재선언 (업로드-R09) — 공개 상태값을 직접 받지 않고 "피드 공개 여부 × 담을 포트폴리오"
     * 조합으로 계산한다. {@code portfolioIds}는 증분이 아니라 전체 재선언이라 목록에 없는 기존 편입은
     * 해제된다. 편입 반영은 {@link ArtworkPortfolioSelectionRequested} 이벤트를 portfolio가 같은
     * 트랜잭션에서 동기 처리한다.
     */
    void updatePublication(String memberId, String artworkId, boolean publishToFeed, List<String> portfolioIds);

    void deleteArtwork(String memberId, String artworkId);

    /**
     * 커뮤니티 작품 피드.
     *
     * @param viewerLanguages 뷰어가 노출받기로 한 게시물 언어(로그인-R16). 비어 있으면 언어 필터를
     *                        적용하지 않는다 — 비로그인 뷰어가 여기에 해당한다
     */
    CursorPage<ArtworkSummaryInfo> getCommunityArtworks(ArtworkField artworkField, AgeRating ageRating,
                                                        List<Language> viewerLanguages,
                                                        String cursor, int size);

    CursorPage<ArtworkSummaryInfo> getMyArtworks(String memberId, String cursor, int size);

    CursorPage<ArtworkSummaryInfo> getTrashArtworks(String memberId, String cursor, int size);

    void restoreArtworks(String memberId, List<String> artworkIds);

    void permanentlyDeleteArtworks(String memberId, List<String> artworkIds);

    /**
     * 작품의 라이브 포트폴리오(작가 페이지·최신 반영형) 편입 여부를 갱신한다.
     * portfolio 모듈이 같은 트랜잭션 안에서 호출한다(docs/design/portfolio-module-design.md §1.2).
     * 고정형(SNAPSHOT) 포트폴리오는 원본 접근 권한에 영향을 주지 않으므로 호출하지 않는다.
     */
    void updatePortfolioInclusion(String artworkId, boolean included);

    /**
     * 검색 색인용 조회 — 뷰어 권한 검사 없이 조회한다. 호출 측(search 모듈)이 status/visibility를 보고
     * 색인 여부를 직접 판단한다(docs/design/search-module-design.md §5.1). 삭제된 작품은 빈 Optional.
     */
    Optional<ArtworkInfo> getArtworkForIndexing(String artworkId);

    /** 검색 전체 재색인용 순회 조회 — status/visibility 필터 없이 전체 작품을 createdAt 오름차순으로 순회한다. */
    CursorPage<ArtworkInfo> getArtworksForReindex(String cursor, int size);
}
