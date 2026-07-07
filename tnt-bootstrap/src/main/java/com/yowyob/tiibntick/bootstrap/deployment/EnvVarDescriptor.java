package com.yowyob.tiibntick.bootstrap.deployment;

import lombok.Builder;
import lombok.Getter;

/**
 * Value object describing a single environment variable required or supported
 * by the TiiBnTick Core Docker image.
 * <p>
 * Used by {@link DockerImageDescriptor} and exposed via the
 * {@code /actuator/tnt/modules} endpoint to aid operators in configuring deployments.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class EnvVarDescriptor {

    private final String name;
    private final String description;
    private final boolean required;
    private final String defaultValue;
    private final EnvCategory category;

    // ── Pre-defined descriptors for all known env vars ────────────────────────

    public static final EnvVarDescriptor KERNEL_BASE_URL = EnvVarDescriptor.builder()
            .name("TNT_KERNEL_BASE_URL")
            .description("Base URL of the Yowyob Kernel (RT-comops) REST API")
            .required(false)
            .defaultValue("http://localhost:8090")
            .category(EnvCategory.KERNEL_YOWYOB)
            .build();

    public static final EnvVarDescriptor KERNEL_API_KEY = EnvVarDescriptor.builder()
            .name("TNT_KERNEL_API_KEY")
            .description("API key for authenticating calls to the Yowyob Kernel")
            .required(true)
            .defaultValue(null)
            .category(EnvCategory.KERNEL_YOWYOB)
            .build();

    public static final EnvVarDescriptor PG_HOST_CORE = EnvVarDescriptor.builder()
            .name("DB_HOST")
            .description("PostgreSQL host for tnt-core schemas")
            .required(true)
            .defaultValue("localhost")
            .category(EnvCategory.DATABASE_PYRAMID)
            .build();

    public static final EnvVarDescriptor PG_PASSWORD = EnvVarDescriptor.builder()
            .name("DB_PASSWORD")
            .description("PostgreSQL password for tnt-core database user")
            .required(true)
            .defaultValue(null)
            .category(EnvCategory.DATABASE_PYRAMID)
            .build();

    public static final EnvVarDescriptor KAFKA_BOOTSTRAP = EnvVarDescriptor.builder()
            .name("KAFKA_BOOTSTRAP")
            .description("Kafka bootstrap servers (comma-separated host:port)")
            .required(true)
            .defaultValue("localhost:9092")
            .category(EnvCategory.MESSAGING)
            .build();

    public static final EnvVarDescriptor REDIS_HOST = EnvVarDescriptor.builder()
            .name("REDIS_HOST")
            .description("Redis server hostname")
            .required(true)
            .defaultValue("localhost")
            .category(EnvCategory.MESSAGING)
            .build();

    public static final EnvVarDescriptor MINIO_URL = EnvVarDescriptor.builder()
            .name("MINIO_ENDPOINT")
            .description("MinIO S3-compatible endpoint URL")
            .required(true)
            .defaultValue("http://localhost:9000")
            .category(EnvCategory.STORAGE)
            .build();

    public static final EnvVarDescriptor MINIO_ACCESS_KEY = EnvVarDescriptor.builder()
            .name("MINIO_ACCESS_KEY")
            .description("MinIO access key (username)")
            .required(true)
            .defaultValue(null)
            .category(EnvCategory.STORAGE)
            .build();

    public static final EnvVarDescriptor MINIO_SECRET_KEY = EnvVarDescriptor.builder()
            .name("MINIO_SECRET_KEY")
            .description("MinIO secret key (password)")
            .required(true)
            .defaultValue(null)
            .category(EnvCategory.STORAGE)
            .build();

    public static final EnvVarDescriptor JWT_ISSUER_URI = EnvVarDescriptor.builder()
            .name("JWT_ISSUER_URI")
            .description("OAuth2 JWT issuer URI (comops-auth-core / YowAuth0)")
            .required(true)
            .defaultValue("http://localhost:9000")
            .category(EnvCategory.SECURITY)
            .build();

    public static final EnvVarDescriptor QR_HMAC_SECRET = EnvVarDescriptor.builder()
            .name("QR_HMAC_SECRET")
            .description("HMAC-SHA256 secret for QR code signing — MUST be strong random in production")
            .required(true)
            .defaultValue(null)
            .category(EnvCategory.SECURITY)
            .build();
}
