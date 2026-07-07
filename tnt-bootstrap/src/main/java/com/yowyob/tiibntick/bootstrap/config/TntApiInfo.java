package com.yowyob.tiibntick.bootstrap.config;

import lombok.Builder;
import lombok.Getter;

/**
 * Value object encapsulating OpenAPI metadata for the TiiBnTick Core Swagger UI.
 * Consumed by {@link TntOpenApiConfig} to build the {@code OpenAPI} bean.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class TntApiInfo {

    @Builder.Default
    private final String title = "TiiBnTick Core API";

    @Builder.Default
    private final String description = """
            TiiBnTick Core — Internal REST API reference.
            Hexagonal Architecture / DDD Modular Monolith.
            Authentication: OAuth2 Bearer JWT (YowAuth0).
            Multi-tenancy: X-Tenant-Id header required.
            Author: MANFOUO Braun — ENSP Yaoundé 2026.
            """;

    @Builder.Default
    private final String version = "0.0.1";

    @Builder.Default
    private final String contactName = "MANFOUO Braun";

    @Builder.Default
    private final String contactEmail = "manfouo.braun@tiibntick.com";

    @Builder.Default
    private final String licenseName = "Proprietary — TiiBnTick / Yowyob";

    private final String kernelVersion;

    public static TntApiInfo defaults() {
        return TntApiInfo.builder().build();
    }
}
