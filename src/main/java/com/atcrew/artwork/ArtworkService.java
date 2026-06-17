package com.atcrew.artwork;

import com.atcrew.common.response.CursorPage;

import java.util.List;

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
}
