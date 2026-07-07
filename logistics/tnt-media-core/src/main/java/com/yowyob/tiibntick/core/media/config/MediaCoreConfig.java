package com.yowyob.tiibntick.core.media.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot auto-configuration for the {@code tnt-media-core} module.
 * <p>
 * Exported from this module and imported by {@code tnt-bootstrap} via
 * {@code @Import(MediaCoreConfig.class)} or through Spring Boot's
 * auto-configuration mechanism.
 * <p>
 * Registers:
 * <ul>
 *   <li>{@link MinioClient} — S3-compatible object storage client</li>
 *   <li>{@link ObjectMapper} — Jackson mapper with Java Time support</li>
 *   <li>R2DBC repositories scan for the {@code tnt.media} package</li>
 *   <li>Scheduling support for the expired-file cleanup job</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MediaCoreProperties.class)
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.media.adapter.persistence")
@EnableScheduling
public class MediaCoreConfig {

    @Bean
    public MinioClient minioClient(MediaCoreProperties properties) {
        log.info("Configuring MinIO client → endpoint: {}", properties.getMinio().getEndpoint());
        return MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(
                        properties.getMinio().getAccessKey(),
                        properties.getMinio().getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "mediaCoreObjectMapper")
    public ObjectMapper mediaCoreObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
