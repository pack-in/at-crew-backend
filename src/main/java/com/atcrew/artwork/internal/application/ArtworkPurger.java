package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkChangedEvent;
import com.atcrew.artwork.ArtworkPermanentlyDeletedEvent;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import java.util.List;
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

    ArtworkPurger(ArtworkRepository artworkRepository, ApplicationEventPublisher eventPublisher) {
        this.artworkRepository = artworkRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
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
     * <p>자료 첨부 key({@code Material.attachmentKeys})는 <b>일부러 넣지 않는다.</b> 클라이언트가 보낸 값을 소유 검증 없이
     * 저장하므로, 다른 사용자의 key(공개 API에 노출된다)를 첨부로 넣고 영구 삭제하면 남의 파일이 지워진다. 첨부 파일은
     * 소유 검증이 생길 때까지 R2에 남는다(누수를 감수한다). 사용자 지정 썸네일 key에도 같은 검증 공백이 있다(#190).
     *
     * <p>지정 썸네일은 이미지 처리 대상이 아니라 media_assets에 행이 없어, 여기서 빠지면 어디서도
     * 지워지지 않고 R2에 남는다 — 후보에 넣는 이유는 이 파일 정리뿐이다. 보존 판정은 V41 색인으로 key마다
     * 조회하므로 후보 구성과 무관하다(docs/design/portfolio-module-design.md §5.6).
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
