package com.yowyob.tiibntick.bootstrap.deployment;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Value object describing the Docker image characteristics for tnt-bootstrap.
 * <p>
 * Exposed via {@code /actuator/info} to inform operators about:
 * <ul>
 *   <li>The required base OS (Debian — mandatory for OR-Tools JNI / glibc)</li>
 *   <li>All required environment variables</li>
 *   <li>JVM arguments recommended for container environments</li>
 *   <li>Health check path and timeout</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class DockerImageDescriptor {

    private final String baseImage;
    private final String baseOs;
    private final List<Integer> exposedPorts;
    private final String healthCheckPath;
    private final int healthCheckTimeoutSeconds;
    private final List<String> jvmArgs;
    private final List<EnvVarDescriptor> environmentVariables;

    /**
     * List of required environment variable names (those with {@code required=true}).
     */
    public static final List<String> REQUIRED_ENV_VARS = List.of(
            "DB_HOST", "DB_PASSWORD", "KAFKA_BOOTSTRAP",
            "REDIS_HOST", "MINIO_ENDPOINT", "MINIO_ACCESS_KEY",
            "MINIO_SECRET_KEY", "JWT_ISSUER_URI", "QR_HMAC_SECRET"
    );

    /**
     * Returns the canonical {@link DockerImageDescriptor} for tnt-bootstrap production.
     */
    public static DockerImageDescriptor production() {
        return DockerImageDescriptor.builder()
                .baseImage("eclipse-temurin:21-jre")
                .baseOs("Debian Bookworm — required for OR-Tools JNI (glibc). Alpine is NOT supported.")
                .exposedPorts(List.of(8080))
                .healthCheckPath("/actuator/health/liveness")
                .healthCheckTimeoutSeconds(300)
                .jvmArgs(List.of(
                        "-XX:+UseContainerSupport",
                        "-XX:MaxRAMPercentage=75.0",
                        "-XX:+UseZGC",
                        "-XX:+ZGenerational",
                        "-Djava.security.egd=file:/dev/./urandom",
                        "-Dfile.encoding=UTF-8"
                ))
                .environmentVariables(List.of(
                        EnvVarDescriptor.KERNEL_BASE_URL,
                        EnvVarDescriptor.KERNEL_API_KEY,
                        EnvVarDescriptor.PG_HOST_CORE,
                        EnvVarDescriptor.PG_PASSWORD,
                        EnvVarDescriptor.KAFKA_BOOTSTRAP,
                        EnvVarDescriptor.REDIS_HOST,
                        EnvVarDescriptor.MINIO_URL,
                        EnvVarDescriptor.MINIO_ACCESS_KEY,
                        EnvVarDescriptor.MINIO_SECRET_KEY,
                        EnvVarDescriptor.JWT_ISSUER_URI,
                        EnvVarDescriptor.QR_HMAC_SECRET
                ))
                .build();
    }

    /**
     * Validates that all required environment variables are set in the current JVM.
     *
     * @return list of missing required environment variable names
     */
    public List<String> validate() {
        return REQUIRED_ENV_VARS.stream()
                .filter(name -> {
                    String value = System.getenv(name);
                    return value == null || value.isBlank();
                })
                .toList();
    }
}
