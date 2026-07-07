package com.yowyob.tiibntick.core.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the {@code tnt-media-core} module.
 * Bound from the {@code tnt.media} prefix in application.yml.
 *
 * @author MANFOUO Braun
 */
@Data
@ConfigurationProperties(prefix = "tnt.media")
public class MediaCoreProperties {

    private Minio minio = new Minio();

    /**
     * HMAC-SHA256 secret key used to sign and verify QR code payloads.
     * MUST be changed in production — use a random 256-bit value.
     */
    private String hmacSecret = "changeme-tnt-qr-hmac-secret-256bits";

    /** Maximum upload size in bytes. Default 20 MB. */
    private long maxUploadBytes = 20 * 1024 * 1024L;

    /** Default presigned URL TTL in seconds. Default 1 hour. */
    private int presignedTtlSeconds = 3600;

    /** Scheduled cleanup interval for expired files (cron expression). */
    private String cleanupCron = "0 0 2 * * *";

    @Data
    public static class Minio {
        /** MinIO server endpoint URL (e.g., http://minio:9000). */
        private String endpoint = "http://localhost:9000";
        /** MinIO access key (username). */
        private String accessKey = "minioadmin";
        /** MinIO secret key (password). */
        private String secretKey = "minioadmin";
        /** Public MinIO endpoint exposed to clients for public file access. */
        private String publicEndpoint = "http://localhost:9000";
    }
}
