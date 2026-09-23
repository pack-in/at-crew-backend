package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.artwork.Material;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작품 영구 삭제의 본체 — 사용자 영구 삭제와 휴지통 보관 기간 만료 자동 삭제(#178)가 함께 쓴다.
 *
 * <p>두 경로가 같은 코드를 거쳐야 후속 처리가 똑같이 적용된다. {@link ArtworkPermanentlyDeletedEvent}를 받은
 * 리스너가 고정형 스냅샷이 참조하는 키를 보존하고, 나머지 R2 파일과 media 자산 행, 포트폴리오 구성 행을 정리한다.
 * 권한·상태 검증은 호출자가 끝낸 뒤 부른다. 호출자의 트랜잭션 안에서만 실행된다({@code MANDATORY}) — 트랜잭션 없이
 * 불리면 행은 지워지는데 커밋 뒤에 도는 리스너(R2 정리·포트폴리오 구성 정리·검색 색인)가 이벤트를 받지 못한다.
 */
@Component
class ArtworkPurger {

    private final ArtworkRepository artworkRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaService mediaService;

    ArtworkPurger(ArtworkRepository artworkRepository, ApplicationEventPublisher eventPublisher,
                  MediaService mediaService) {
        this.artworkRepository = artworkRepository;
        this.eventPublisher = eventPublisher;
        this.mediaService = mediaService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void purge(List<Artwork> artworks) {
        // key 목록은 행을 지우기 전에 만든다 — media 행은 리스너가 나중에 정리한다.
        Map<String, List<String>> keysByArtwork = artworks.stream()
                .collect(Collectors.toMap(Artwork::getId, this::allImageKeys));
        artworkRepository.deleteAll(artworks);
        for (Artwork artwork : artworks) {
            eventPublisher.publishEvent(new ArtworkPermanentlyDeletedEvent(artwork.getId(),
                    keysByArtwork.get(artwork.getId())));
            eventPublisher.publishEvent(new ArtworkChangedEvent(artwork.getId()));
        }
    }

    /**
     * 영구 삭제 대상 R2 key 전체 — 이미지 4종, 사용자 지정 썸네일, 자료 첨부.
     *
     * <p>지정 썸네일과 자료 첨부는 이미지 처리 대상이 아니라 media_assets에 행이 없다. 여기서 빠지면 어디서도
     * 지워지지 않고 R2에 남으므로 후보에 넣는다. 한동안 첨부를 뺐던 것은 소유 검증이 없어 남의 key가 섞일 수
     * 있었기 때문인데(#188), 업로드 key에 소유자 서명이 들어가면서(#190) 그 전제가 사라졌다.
     *
     * <p>보존 판정은 V41 색인으로 key마다 조회하므로 후보 구성과 무관하다(docs/design/portfolio-module-design.md §5.6).
     */
    private List<String> allImageKeys(Artwork artwork) {
        Stream<String> imageKeys = mediaService.getAssets(MediaOwnerType.ARTWORK, artwork.getId()).stream()
                .flatMap(img -> Stream.of(
                        img.originalKey(),
                        img.thumbKey(),
                        img.thumbAdultKey(),
                        img.originalAvifKey()
                ));
        Stream<String> attachmentKeys = artwork.getMaterials().stream()
                .map(Material::getAttachmentKeys).filter(Objects::nonNull).flatMap(List::stream);
        return Stream.concat(Stream.concat(imageKeys, attachmentKeys), Stream.of(artwork.getThumbnailKey()))
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
    }
}
