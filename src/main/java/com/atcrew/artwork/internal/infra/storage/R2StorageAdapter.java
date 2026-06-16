package com.atcrew.artwork.internal.infra.storage;

import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class R2StorageAdapter implements ArtworkStoragePort {

    private static final Logger log = LoggerFactory.getLogger(R2StorageAdapter.class);

    private final R2Properties props;
    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final RestClient restClient;

    R2StorageAdapter(R2Properties props) {
        this.props = props;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        URI endpoint = URI.create(props.endpoint());
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(Region.of("auto"))
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .build();
        this.restClient = RestClient.create();
    }

    @Override
    public String generatePresignedPutUrl(String key, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(props.presignExpirationMinutes()))
                            .putObjectRequest(putRequest)
                            .build());
            return presigned.url().toString();
        } catch (Exception e) {
            log.error("R2 presigned URL 생성 실패: key={}", key, e);
            throw new ArtworkException(ArtworkErrorCode.PRESIGN_FAILED, e.getMessage());
        }
    }

    @Override
    public void triggerWorker(String artworkId, List<String> imageKeys) {
        try {
            restClient.post()
                    .uri(props.workerTriggerUrl())
                    .header("X-Callback-Secret", props.callbackSecret())
                    .body(Map.of("artworkId", artworkId, "imageKeys", imageKeys))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Worker 트리거 실패: artworkId={} keys={}", artworkId, imageKeys, e);
        }
    }

    @Override
    public void deleteFiles(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        try {
            List<ObjectIdentifier> identifiers = keys.stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            if (identifiers.isEmpty()) return;
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(props.bucket())
                    .delete(Delete.builder().objects(identifiers).build())
                    .build());
        } catch (Exception e) {
            log.error("R2 파일 삭제 실패: keys={}", keys, e);
            throw new RuntimeException("R2 파일 삭제 실패", e);
        }
    }
}
