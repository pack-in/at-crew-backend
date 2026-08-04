package com.atcrew.media.internal.infra.storage;

import com.atcrew.media.MediaOwnerType;
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

// artwork에 같은 단순명의 클래스가 남아 있는 동안의 빈 이름 충돌 회피 — artwork 정리 후에는 불필요.
@Component("mediaR2StorageAdapter")
class R2StorageAdapter implements ArtworkStoragePort {
    private static final Logger log = LoggerFactory.getLogger(R2StorageAdapter.class);
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
    @Override public void triggerWorker(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                        MediaVariantProfile variantProfile) {
        try {
            restClient.post().uri(props.workerTriggerUrl()).header("X-Callback-Secret", props.callbackSecret())
                    .body(Map.of("ownerType", ownerType.name(), "ownerId", ownerId, "imageKeys", imageKeys,
                            "variantProfile", variantProfile.name())).retrieve().toBodilessEntity();
        } catch (Exception e) { log.error("Worker 트리거 실패: ownerType={} ownerId={} keys={}", ownerType, ownerId, imageKeys, e); }
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
