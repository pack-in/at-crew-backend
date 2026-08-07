package com.atcrew.search.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.MaterialInfo;
import com.atcrew.search.internal.domain.ArtworkSearchDocument;

import java.util.List;
import java.util.stream.Collectors;

/** {@code ArtworkInfo} → {@code ArtworkSearchDocument} 변환. */
class ArtworkSearchMapper {

    private ArtworkSearchMapper() {
    }

    static ArtworkSearchDocument toDocument(ArtworkInfo info) {
        // ES에는 enum 상수 이름(keyword)으로 색인한다 — 필터도 같은 이름으로 매칭한다(§9-2).
        List<String> materialTargets = info.materials() == null ? List.of()
                : info.materials().stream()
                        .map(MaterialInfo::targets)
                        .filter(java.util.Objects::nonNull)
                        .flatMap(List::stream)
                        .distinct()
                        .map(Enum::name)
                        .collect(Collectors.toList());

        String thumbnailKey;
        String thumbnailAdultKey;
        if (info.thumbnailKey() != null) {
            thumbnailKey = info.thumbnailKey();
            thumbnailAdultKey = null;
        } else {
            ArtworkImageInfo rep = representativeImage(info);
            thumbnailKey = rep != null ? rep.thumbKey() : null;
            thumbnailAdultKey = rep != null ? rep.thumbAdultKey() : null;
        }

        return new ArtworkSearchDocument(
                info.id(),
                info.title(),
                info.description(),
                info.tags(),
                info.authorId(),
                info.authorName(),
                info.authorHandle(),
                info.artworkField() != null ? info.artworkField().name() : null,
                info.creativeType() != null ? info.creativeType().name() : null,
                info.ageRating() != null ? info.ageRating().name() : null,
                info.roles() == null ? List.of() : info.roles().stream().map(Enum::name).toList(),
                info.genres() == null ? List.of() : info.genres().stream().map(Enum::name).toList(),
                materialTargets,
                thumbnailKey,
                thumbnailAdultKey,
                info.createdAt(),
                info.updatedAt()
        );
    }

    private static ArtworkImageInfo representativeImage(ArtworkInfo info) {
        List<ArtworkImageInfo> images = info.images();
        int index = info.representativeImageIndex();
        if (images == null || index < 0 || index >= images.size()) {
            return null;
        }
        return images.get(index);
    }
}
