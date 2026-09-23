package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.media.RetainedMediaKeyProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 살아 있는 작품이 참조 중인 R2 key 보존 판정(#216·#229).
 *
 * <p>사용자 지정 썸네일과 자료 첨부는 media 자산 행이 없는 raw key다. "media_assets에 없으면 아무도 안 쓴다"는
 * 판정으로 지우면 이 둘이 사라진다. 또 같은 회원이 두 작품에 같은 첨부 key를 넣을 수 있어, 한 작품을 영구
 * 삭제할 때 다른 작품이 쓰는 파일을 지울 수 있었다(#229).
 *
 * <p>영구 삭제 경로에서는 작품 행이 먼저 지워진 뒤 이 판정이 돌기 때문에(리스너가 커밋 이후에 실행된다),
 * 여기서 걸리는 것은 <b>다른 작품</b>이 쓰는 key뿐이다.
 */
@Component
class ArtworkRetainedMediaKeyProvider implements RetainedMediaKeyProvider {

    private final ArtworkRepository artworkRepository;

    ArtworkRetainedMediaKeyProvider(ArtworkRepository artworkRepository) {
        this.artworkRepository = artworkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> retainedKeys(Collection<String> candidateKeys) {
        if (candidateKeys == null) {
            return Set.of();
        }
        List<String> candidates = candidateKeys.stream()
                .filter(key -> key != null && !key.isBlank()).distinct().toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<String> retained = new HashSet<>(artworkRepository.findUsedThumbnailKeys(candidates));
        retained.addAll(artworkRepository.findUsedAttachmentKeys(candidates));
        return retained;
    }
}
