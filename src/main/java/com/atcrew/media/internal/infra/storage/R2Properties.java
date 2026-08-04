package com.atcrew.media.internal.infra.storage;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "cloudflare.r2")
public record R2Properties(String endpoint, String accessKey, String secretKey, String bucket,
                           int presignExpirationMinutes, String workerTriggerUrl, String callbackSecret) { }
