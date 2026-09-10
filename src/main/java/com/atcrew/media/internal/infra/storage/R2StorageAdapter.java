package com.atcrew.media.internal.infra.storage;

import com.atcrew.media.MediaOwnerType;
import com.atcrew.media.MediaQualityTier;
import com.atcrew.media.MediaVariantProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class R2StorageAdapter implements ArtworkStoragePort {
    private static final Logger log = LoggerFactory.getLogger(R2StorageAdapter.class);
    /** Worker에 넘기는 원본 읽기 URL의 유효 시간. 재시도도 매번 새로 발급하므로 짧게 잡을 이유가 없다. */
    private static final long SOURCE_URL_EXPIRATION_MINUTES = 60;
    private final R2Properties props; private final S3Presigner presigner; private final S3Client s3Client;
    private final RestClient restClient;
    R2StorageAdapter(R2Properties props) {
        this.props = props;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        URI endpoint = URI.create(props.endpoint());
        presigner = S3Presigner.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.of("auto")).build();
        s3Client = S3Client.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.of("auto")).forcePathStyle(true).build();
        restClient = RestClient.create();
    }
    @Override public String generatePresignedPutUrl(String key, String contentType) {
        try {
            var put = PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType).build();
            return presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(props.presignExpirationMinutes())).putObjectRequest(put).build()).url().toString();
        } catch (Exception e) { log.error("R2 presigned URL 생성 실패: key={}", key, e); throw new IllegalStateException("R2 presigned URL 생성 실패", e); }
    }
    /**
     * Worker는 원본 바이트를 직접 읽지 않고 {@code fetch(url, {cf:{image}})}로 변환한다 — R2 바인딩으로
     * 넘기는 경로는 입력이 20MB로 막혀 있고(라이트 실데이터 기준 2.89%가 초과), URL 경로는 100MB까지
     * 받는다. 그래서 키마다 읽기용 서명 URL을 함께 실어 보낸다.
     *
     * <p>만료는 넉넉히 준다. 변환은 트리거 응답을 기다리지 않고 백그라운드에서 진행되며, 큰 원본의 AVIF
     * 인코딩은 수십 초가 걸릴 수 있다.
     */
    @Override public void triggerWorker(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                        MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
        try {
            List<String> sourceUrls = imageKeys.stream().map(this::generatePresignedGetUrl).toList();
            restClient.post().uri(props.workerTriggerUrl()).header("X-Callback-Secret", props.callbackSecret())
                    .body(Map.of("ownerType", ownerType.name(), "ownerId", ownerId, "imageKeys", imageKeys,
                            "sourceUrls", sourceUrls,
                            "variantProfile", variantProfile.name(),
                            "qualityTier", qualityTier.name())).retrieve().toBodilessEntity();
        } catch (Exception e) { log.error("Worker 트리거 실패: ownerType={} ownerId={} keys={}", ownerType, ownerId, imageKeys, e); }
    }
    private String generatePresignedGetUrl(String key) {
        var get = GetObjectRequest.builder().bucket(props.bucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(SOURCE_URL_EXPIRATION_MINUTES))
                .getObjectRequest(get).build()).url().toString();
    }
    @Override public void deleteFiles(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        try {
            var identifiers = keys.stream().filter(k -> k != null && !k.isBlank())
                    .map(k -> ObjectIdentifier.builder().key(k).build()).toList();
            if (identifiers.isEmpty()) return;
            s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(props.bucket())
                    .delete(Delete.builder().objects(identifiers).build()).build());
        } catch (Exception e) { log.error("R2 파일 삭제 실패: keys={}", keys, e); throw new IllegalStateException("R2 파일 삭제 실패", e); }
    }
}
