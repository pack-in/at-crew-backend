package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.ArtworkSummaryInfo;
import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.MaterialInfo;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.artwork.ArtworkImage;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import com.atcrew.member.MemberInfo;

import java.util.List;

class ArtworkMapper {

    private ArtworkMapper() {
    }

    static ArtworkInfo toInfo(Artwork artwork, MemberInfo author) {
        return new ArtworkInfo(
                artwork.getId(),
                artwork.getAuthorId(),
                author != null ? author.name() : null,
                author != null ? author.handle() : null,
                artwork.getTitle(),
                artwork.getDescription(),
                artwork.getImages().stream().map(ArtworkMapper::toImageInfo).toList(),
                artwork.getRepresentativeImageIndex(),
                artwork.getThumbnailKey(),
                artwork.getImageLayoutType(),
                artwork.getArtworkField(),
                artwork.getCreativeType(),
                artwork.getRoles(),
                artwork.getGenres(),
                artwork.getTags(),
                artwork.getTools(),
                artwork.getWorkDuration(),
                artwork.getCutCount(),
                artwork.getVideoLinks(),
                artwork.getAgeRating(),
                artwork.getVisibility(),
                artwork.getMaterials().stream()
                        .map(m -> new MaterialInfo(m.getName(), m.getTargets(),
                                m.getAttachmentKeys(), m.getLinks()))
                        .toList(),
                artwork.getStatus(),
                artwork.getCreatedAt(),
                artwork.getUpdatedAt()
        );
    }

    static ArtworkSummaryInfo toSummaryInfo(Artwork artwork, MemberInfo author) {
        // 사용자 지정 썸네일 우선, 없으면 대표 이미지의 Worker 생성 썸네일 사용
        String thumbKey;
        String thumbAdultKey;
        if (artwork.getThumbnailKey() != null) {
            thumbKey = artwork.getThumbnailKey();
            thumbAdultKey = null;
        } else {
            ArtworkImage repImage = artwork.getRepresentativeImage();
            thumbKey = repImage != null ? repImage.getThumbKey() : null;
            thumbAdultKey = repImage != null ? repImage.getThumbAdultKey() : null;
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
                artwork.getStatus(),
                artwork.getCreatedAt()
        );
    }

    static ArtworkImageInfo toImageInfo(ArtworkImage image) {
        return new ArtworkImageInfo(
                image.getOriginalKey(),
                image.getThumbKey(),
                image.getThumbAdultKey(),
                image.getOriginalAvifKey(),
                image.getProcessingStatus()
        );
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
