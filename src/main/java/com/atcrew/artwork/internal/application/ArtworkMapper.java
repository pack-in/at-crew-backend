package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ImageProcessingStatus;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.MaterialInfo;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import com.atcrew.media.MediaAssetInfo;
import com.atcrew.media.MediaProcessingStatus;
import com.atcrew.member.MemberInfo;

import java.util.List;

class ArtworkMapper {

    private ArtworkMapper() {
    }

    /** 이미지는 media가 갖는다(#193) — 호출자가 읽어 순서대로 넘긴다. */
    static ArtworkInfo toInfo(Artwork artwork, MemberInfo author, List<MediaAssetInfo> images) {
        return new ArtworkInfo(
                artwork.getId(),
                artwork.getAuthorId(),
                author != null ? author.name() : null,
                author != null ? author.handle() : null,
                artwork.getTitle(),
                artwork.getDescription(),
                images.stream().map(ArtworkMapper::toImageInfo).toList(),
                artwork.getRepresentativeImageIndex(),
                artwork.getThumbnailKey(),
                artwork.getImageLayoutType(),
                artwork.getArtworkField(),
                artwork.getCreativeType(),
                artwork.getRoles(),
                artwork.getGenres(),
                artwork.getCustomTags(),
                artwork.getTags(),
                artwork.getTools(),
                artwork.getWorkDuration(),
                artwork.getCutCount(),
                artwork.getVideoLinks(),
                artwork.getAgeRating(),
                artwork.getLanguages(),
                artwork.getVisibility(),
                artwork.isPortfolioIncluded(),
                artwork.isBlocked(),
                artwork.getMaterials().stream()
                        .map(m -> new MaterialInfo(m.getName(), m.getTargets(), m.getCustomTargets(),
                                m.getAttachmentKeys(), m.getLinks()))
                        .toList(),
                artwork.getStatus(),
                artwork.getCreatedAt(),
                artwork.getUpdatedAt()
        );
    }

    static ArtworkSummaryInfo toSummaryInfo(Artwork artwork, MemberInfo author, List<MediaAssetInfo> images) {
        // 사용자 지정 썸네일 우선, 없으면 대표 이미지의 Worker 생성 썸네일 사용
        String thumbKey;
        String thumbAdultKey;
        if (artwork.getThumbnailKey() != null) {
            thumbKey = artwork.getThumbnailKey();
            thumbAdultKey = null;
        } else {
            MediaAssetInfo repImage = representativeOf(artwork, images);
            thumbKey = repImage != null ? repImage.thumbKey() : null;
            thumbAdultKey = repImage != null ? repImage.thumbAdultKey() : null;
        }
        return new ArtworkSummaryInfo(
                artwork.getId(),
                artwork.getAuthorId(),
                author != null ? author.name() : null,
                author != null ? author.handle() : null,
                artwork.getTitle(),
                thumbKey,
                thumbAdultKey,
                artwork.getArtworkField(),
                artwork.getTags(),
                artwork.getAgeRating(),
                artwork.getVisibility(),
                artwork.isBlocked(),
                artwork.getStatus(),
                artwork.getCreatedAt()
        );
    }

    /** 대표 이미지 — 목록이 줄어 인덱스가 벗어나면 마지막 이미지로 자른다(기존 동작). */
    static MediaAssetInfo representativeOf(Artwork artwork, List<MediaAssetInfo> images) {
        if (images == null || images.isEmpty()) return null;
        return images.get(Math.min(artwork.getRepresentativeImageIndex(), images.size() - 1));
    }

    static ArtworkImageInfo toImageInfo(MediaAssetInfo image) {
        return new ArtworkImageInfo(
                image.originalKey(),
                image.thumbKey(),
                image.thumbAdultKey(),
                image.originalAvifKey(),
                toImageStatus(image.status())
        );
    }

    static ImageProcessingStatus toImageStatus(MediaProcessingStatus status) {
        return switch (status) {
            case PENDING -> ImageProcessingStatus.PENDING;
            case DONE -> ImageProcessingStatus.DONE;
            case FAILED -> ImageProcessingStatus.FAILED;
        };
    }

    static BookmarkFolderInfo toFolderInfo(BookmarkFolder folder) {
        return new BookmarkFolderInfo(
                folder.getId(),
                folder.getName(),
                folder.getSortOrder(),
                folder.getCreatedAt()
        );
    }

    static BookmarkEntryInfo toEntryInfo(BookmarkEntry entry, ArtworkSummaryInfo artworkSummary) {
        return new BookmarkEntryInfo(
                entry.getId(),
                entry.getArtworkId(),
                entry.getFolderId(),
                entry.getSavedAt(),
                artworkSummary
        );
    }
}
