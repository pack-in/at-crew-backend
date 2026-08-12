package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkInfo;
import com.atcrew.artwork.Visibility;
import com.atcrew.portfolio.PortfolioArtworkCardInfo;
import com.atcrew.portfolio.PortfolioCoverThumbnailInfo;
import com.atcrew.portfolio.PortfolioInfo;
import com.atcrew.portfolio.PortfolioSelectableInfo;
import com.atcrew.portfolio.PortfolioSummaryInfo;
import com.atcrew.portfolio.internal.domain.Portfolio;
import com.atcrew.portfolio.internal.domain.PortfolioItemSnapshot;

import java.util.List;

class PortfolioMapper {

    private PortfolioMapper() {
    }

    static PortfolioInfo toInfo(Portfolio portfolio, List<PortfolioArtworkCardInfo> artworks) {
        return new PortfolioInfo(
                portfolio.getId(),
                portfolio.getKind(),
                portfolio.getReflectionType(),
                portfolio.getTitle(),
                portfolio.getShareSlug(),
                portfolio.getItemCount(),
                artworks,
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    // 커버 썸네일은 별도 조회가 필요하므로 서비스 레이어에서 채워 넘긴다(toInfo의 artworks와 동일한 형태).
    static PortfolioSummaryInfo toSummaryInfo(Portfolio portfolio,
                                              List<PortfolioCoverThumbnailInfo> coverThumbnails) {
        return new PortfolioSummaryInfo(
                portfolio.getId(),
                portfolio.getKind(),
                portfolio.getReflectionType(),
                portfolio.getTitle(),
                portfolio.getShareSlug(),
                portfolio.getItemCount(),
                coverThumbnails,
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    static PortfolioSelectableInfo toSelectableInfo(Portfolio portfolio) {
        return new PortfolioSelectableInfo(
                portfolio.getId(),
                portfolio.getKind(),
                portfolio.getTitle(),
                portfolio.getItemCount()
        );
    }

    // 카드 썸네일 규칙은 ArtworkMapper.toSummaryInfo와 동일하게 맞춘다 —
    // 사용자 지정 썸네일 우선, 없으면 대표 이미지의 Worker 생성 썸네일을 쓴다.
    static PortfolioArtworkCardInfo toCardInfo(ArtworkInfo artwork) {
        String thumbKey;
        String thumbAdultKey;
        if (artwork.thumbnailKey() != null) {
            thumbKey = artwork.thumbnailKey();
            thumbAdultKey = null;
        } else {
            ArtworkImageInfo representative = representativeImage(artwork);
            thumbKey = representative != null ? representative.thumbKey() : null;
            thumbAdultKey = representative != null ? representative.thumbAdultKey() : null;
        }
        return new PortfolioArtworkCardInfo(
                artwork.id(),
                artwork.title(),
                thumbKey,
                thumbAdultKey,
                artwork.ageRating(),
                artwork.artworkField(),
                artwork.visibility(),
                artwork.createdAt()
        );
    }

    /**
     * 고정형 카드는 스냅샷 컬럼만으로 채운다(§2.3) — 원본을 다시 조회하지 않는다.
     *
     * <p>{@code visibility}는 항상 {@code PUBLIC}으로 고정한다. 스냅샷은 생성 시점 구성이 얼어붙어
     * 원본의 비공개 전환·삭제에 영향받지 않아야 하므로(§5.1), 원본 공개 범위를 끌어오면 정책이 깨진다.
     */
    static PortfolioArtworkCardInfo toCardInfo(PortfolioItemSnapshot snapshot) {
        return new PortfolioArtworkCardInfo(
                snapshot.getSourceArtworkId(),
                snapshot.getTitle(),
                snapshot.getThumbKey(),
                snapshot.getThumbAdultKey(),
                snapshot.getAgeRating(),
                snapshot.getArtworkField(),
                Visibility.PUBLIC,
                snapshot.getSourceCreatedAt()
        );
    }

    // 커버 썸네일 판정은 카드와 동일해야 하므로 toCardInfo 결과에서 썸네일 키만 뽑아 쓴다.
    static PortfolioCoverThumbnailInfo toCoverThumbnailInfo(ArtworkInfo artwork) {
        PortfolioArtworkCardInfo card = toCardInfo(artwork);
        return new PortfolioCoverThumbnailInfo(card.thumbKey(), card.thumbAdultKey());
    }

    /** 고정형 커버도 스냅샷 컬럼만으로 채운다(§5.1) — 원본을 다시 조회하지 않는다. */
    static PortfolioCoverThumbnailInfo toCoverThumbnailInfo(PortfolioItemSnapshot snapshot) {
        return new PortfolioCoverThumbnailInfo(snapshot.getThumbKey(), snapshot.getThumbAdultKey());
    }

    private static ArtworkImageInfo representativeImage(ArtworkInfo artwork) {
        List<ArtworkImageInfo> images = artwork.images();
        int index = artwork.representativeImageIndex();
        if (images == null || index < 0 || index >= images.size()) {
            return null;
        }
        return images.get(index);
    }
}
