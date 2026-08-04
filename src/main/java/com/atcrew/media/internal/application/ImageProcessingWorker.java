package com.atcrew.media.internal.application;
import com.atcrew.media.*;
import com.atcrew.media.internal.infra.storage.ArtworkStoragePort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.List;
// 빈 이름을 명시한다 — artwork 모듈에 같은 단순명의 클래스가 남아 있는 동안 스캔 시 이름이 충돌한다.
// artwork 리팩터링으로 그쪽 클래스가 제거되면 이 한정자는 없어도 된다.
@Component("mediaImageProcessingWorker")
class ImageProcessingWorker {
    private final ArtworkStoragePort storagePort;
    ImageProcessingWorker(ArtworkStoragePort storagePort) { this.storagePort = storagePort; }
    @Async void triggerAsync(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                             MediaVariantProfile variantProfile) {
        storagePort.triggerWorker(ownerType, ownerId, imageKeys, variantProfile);
    }
}
