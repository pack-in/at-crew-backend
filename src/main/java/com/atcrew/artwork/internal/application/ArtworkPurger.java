package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 작품 영구 삭제의 본체 — 사용자 영구 삭제와 휴지통 보관 기간 만료 자동 삭제(#178)가 함께 쓴다.
 *
 * <p>두 경로가 같은 코드를 거쳐야 후속 처리가 똑같이 적용된다. {@link ArtworkPermanentlyDeletedEvent}를 받은
 * 리스너가 고정형 스냅샷이 참조하는 키를 보존하고, 나머지 R2 파일과 media 자산 행, 포트폴리오 구성 행을 정리한다.
 * 권한·상태 검증은 호출자가 끝낸 뒤 부른다. 호출자의 트랜잭션 안에서 실행된다.
 */
@Component
class ArtworkPurger {

    private final ArtworkRepository artworkRepository;
    private final ApplicationEventPublisher eventPublisher;

    ArtworkPurger(ArtworkRepository artworkRepository, ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.eventPublisher = eventPublisher;
    }

    void purge(List<Artwork> artworks) {
        artworkRepository.deleteAll(artworks);
        for (Artwork artwork : artworks) {
            eventPublisher.publishEvent(new ArtworkPermanentlyDeletedEvent(artwork.getId(), allImageKeys(artwork)));
            eventPublisher.publishEvent(new ArtworkChangedEvent(artwork.getId()));
        }
    }

    /**
     * 영구 삭제 대상 R2 key 전체 — 이미지 4종에 <b>사용자 지정 썸네일 key</b>까지 포함한다.
     *
     * <p>지정 썸네일은 이미지 처리 대상이 아니라 media_assets에 행이 없어, 여기서 빠지면 어디서도
     * 지워지지 않고 R2에 남는다. 더 중요한 것은 고정형 스냅샷 보존 판정이 이 key로 스냅샷을 찾는다는
     * 점이다({@code PortfolioItemSnapshot.thumb_key}) — 후보 목록에 없으면 스냅샷이 매칭되지 않아
     * 그 스냅샷이 참조 중인 상세 이미지까지 삭제된다(docs/design/portfolio-module-design.md §5.6).
     */
    private List<String> allImageKeys(Artwork artwork) {
        Stream<String> imageKeys = artwork.getImages().stream()
                .flatMap(img -> Stream.of(
                        img.getOriginalKey(),
                        img.getThumbKey(),
                        img.getThumbAdultKey(),
                        img.getOriginalAvifKey()
                ));
        return Stream.concat(imageKeys, Stream.of(artwork.getThumbnailKey()))
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
    }
}
