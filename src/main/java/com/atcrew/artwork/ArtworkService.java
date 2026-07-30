package com.atcrew.artwork;

import com.atcrew.common.response.CursorPage;

import java.util.List;
import java.util.Optional;

public interface ArtworkService {

    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes);

    ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command);

    ArtworkInfo getArtwork(String artworkId, String viewerMemberId);

    ArtworkStatus getArtworkStatus(String memberId, String artworkId);

    ArtworkInfo updateArtwork(String memberId, String artworkId, UpdateArtworkCommand command);

    void updateVisibility(String memberId, String artworkId, Visibility visibility);

    void deleteArtwork(String memberId, String artworkId);

    CursorPage<ArtworkSummaryInfo> getCommunityArtworks(ArtworkField artworkField, AgeRating ageRating,
                                                        String cursor, int size);

    CursorPage<ArtworkSummaryInfo> getMyArtworks(String memberId, String cursor, int size);

    CursorPage<ArtworkSummaryInfo> getTrashArtworks(String memberId, String cursor, int size);

    void restoreArtworks(String memberId, List<String> artworkIds);

    void permanentlyDeleteArtworks(String memberId, List<String> artworkIds);

    void handleImageProcessedCallback(ImageProcessedCallbackCommand command);

    /**
     * 검색 색인용 조회 — 뷰어 권한 검사 없이 조회한다. 호출 측(search 모듈)이 status/visibility를 보고
     * 색인 여부를 직접 판단한다(docs/design/search-module-design.md §5.1). 삭제된 작품은 빈 Optional.
     */
    Optional<ArtworkInfo> getArtworkForIndexing(String artworkId);

    /** 검색 전체 재색인용 순회 조회 — status/visibility 필터 없이 전체 작품을 createdAt 오름차순으로 순회한다. */
    CursorPage<ArtworkInfo> getArtworksForReindex(String cursor, int size);
}
