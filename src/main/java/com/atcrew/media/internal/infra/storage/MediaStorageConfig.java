package com.atcrew.media.internal.infra.storage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@EnableConfigurationProperties(R2Properties.class)
class MediaStorageConfig { }
